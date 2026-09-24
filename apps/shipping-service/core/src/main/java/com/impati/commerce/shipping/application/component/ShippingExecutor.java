package com.impati.commerce.shipping.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.CarrierEventCommand;
import com.impati.commerce.shipping.application.port.in.CarrierEventResult;
import com.impati.commerce.shipping.application.port.in.CarrierOperationRecoveryUseCase;
import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import com.impati.commerce.shipping.application.port.out.CarrierEventRecord;
import com.impati.commerce.shipping.application.port.out.CarrierGateway;
import com.impati.commerce.shipping.application.port.out.CarrierOperationRepository;
import com.impati.commerce.shipping.application.port.out.ShipmentEventRepository;
import com.impati.commerce.shipping.application.port.out.ShipmentRepository;
import com.impati.commerce.shipping.application.port.out.TransactionSection;
import com.impati.commerce.shipping.domain.CarrierOperation;
import com.impati.commerce.shipping.domain.CarrierOperation.Type;
import com.impati.commerce.shipping.domain.ShipmentEvent;
import com.impati.commerce.shipping.domain.ShippingModels.CancellationStatus;
import com.impati.commerce.shipping.domain.ShippingModels.EventDecision;
import com.impati.commerce.shipping.domain.ShippingModels.RegistrationStatus;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import com.impati.commerce.shipping.domain.ShippingModels.ShipmentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShippingExecutor implements ShippingUseCase, CarrierOperationRecoveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ShippingExecutor.class);
    private static final Duration OPERATION_LEASE = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private final ShipmentRepository shipmentRepository;
    private final CarrierGateway carrierGateway;
    private final CarrierOperationRepository operationRepository;
    private final TransactionSection transactions;
    private final ShipmentEventRepository shipmentEventRepository;
    private final Clock clock;

    public ShippingExecutor(
            ShipmentRepository shipmentRepository,
            CarrierGateway carrierGateway,
            CarrierOperationRepository operationRepository,
            TransactionSection transactions,
            ShipmentEventRepository shipmentEventRepository,
            Clock clock
    ) {
        this.shipmentRepository = shipmentRepository;
        this.carrierGateway = carrierGateway;
        this.operationRepository = operationRepository;
        this.transactions = transactions;
        this.shipmentEventRepository = shipmentEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ShipmentDetails create(String orderId, String memberId, ShipmentAddress address) {
        var shipmentAddress = ShipmentMapper.toAddress(address);
        var existing = shipmentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return sameShipment(existing.get(), memberId, shipmentAddress);
        }
        var shipment = new Shipment(orderId, memberId, shipmentAddress);
        if (!shipmentRepository.insertIfAbsent(shipment)) {
            return sameShipment(shipmentRepository.findByOrderId(orderId)
                            .orElseThrow(() -> DomainException.conflict("shipment creation raced without a result")),
                    memberId, shipmentAddress);
        }
        return ShipmentMapper.toDetails(shipment);
    }

    @Override
    public ShipmentDetails get(String shipmentId) {
        return ShipmentMapper.toDetails(getShipment(shipmentId));
    }

    @Override
    public ShipmentDetails getForOrder(String orderId) {
        return ShipmentMapper.toDetails(shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> DomainException.notFound("shipment not found for order")));
    }

    @Override
    public ShipmentDetails completePacking(String shipmentId) {
        var operationKey = transactions.required(() -> {
            var shipment = getShipmentForUpdate(shipmentId);
            if (shipment.registrationStatus() == RegistrationStatus.CONFIRMED) {
                return null;
            }
            if (shipment.requestRegistration()) {
                shipmentRepository.save(shipment);
            }
            var key = CarrierOperation.registrationKey(shipment.id());
            operationRepository.insertIfAbsent(CarrierOperation.pending(key, shipment.id(), Type.REGISTER, now()));
            return key;
        });
        execute(operationKey);
        failIfRejected(operationKey, "carrier registration was rejected");
        return ShipmentMapper.toDetails(getShipment(shipmentId));
    }

    @Override
    public int recoverPendingOperations(int batchSize) {
        var operations = operationRepository.claimDue(batchSize, OPERATION_LEASE);
        operations.forEach(this::executeClaimed);
        return operations.size();
    }

    @Override
    public ShipmentDetails cancel(String shipmentId) {
        var operationKey = transactions.required(() -> {
            var shipment = getShipmentForUpdate(shipmentId);
            if (shipment.status() == ShipmentStatus.CANCELLED) {
                return null;
            }
            if (shipment.requestCancellation()) {
                shipmentRepository.save(shipment);
            }
            var key = CarrierOperation.cancellationKey(shipment.id());
            operationRepository.insertIfAbsent(CarrierOperation.pending(key, shipment.id(), Type.CANCEL, now()));
            return key;
        });
        execute(operationKey);
        var shipment = getShipment(shipmentId);
        if (shipment.cancellationStatus() == CancellationStatus.ATTENTION_REQUIRED) {
            throw DomainException.downstreamError("carrier cancellation requires operational attention");
        }
        return ShipmentMapper.toDetails(shipment);
    }

    @Override
    @Transactional
    public CarrierEventResult receive(CarrierEventCommand command) {
        var shipment = shipmentRepository.findByCarrierAndTrackingForUpdate(command.carrierCode(), command.trackingNumber())
                .orElseThrow(() -> DomainException.notFound("shipment not found for carrier tracking"));
        var receivedAt = now();
        var received = new CarrierEventRecord(
                command.eventId(), shipment.id(), command.carrierCode(), command.trackingNumber(),
                command.type().name(), command.occurredAt(), receivedAt, receivedAt,
                "RECEIVED", shipment.status().name(), 0
        );
        if (!shipmentRepository.insertCarrierEventIfAbsent(received)) {
            var existing = shipmentRepository.findCarrierEvent(command.eventId())
                    .orElseThrow(() -> new IllegalStateException("duplicate carrier event disappeared"));
            if (!sameEvent(existing, command)) {
                log.warn("carrier event id reused with different content existing={} incoming={}", existing, command);
                return new CarrierEventResult(command.eventId(), existing.shipmentId(), EventDecision.CONFLICT.name(),
                        existing.shipmentStatus());
            }
            shipmentRepository.recordDuplicateCarrierEvent(command.eventId(), receivedAt);
            return new CarrierEventResult(command.eventId(), shipment.id(), "DUPLICATE", shipment.status().name());
        }

        var decision = shipment.applyCarrierEvent(command.type(), command.occurredAt());
        if (decision == EventDecision.APPLIED || decision == EventDecision.NO_TRANSITION) {
            shipmentRepository.save(shipment);
        }
        if (decision == EventDecision.APPLIED) {
            shipmentEventRepository.save(ShipmentEvent.occurred(
                    "SHIPMENT_" + command.type().name(), shipment, command.occurredAt()));
        }
        shipmentRepository.completeCarrierEvent(command.eventId(), decision.name(), shipment.status().name());
        if (decision == EventDecision.CONFLICT) {
            log.warn("carrier event conflicts with shipment terminal state event={} shipment={} status={}",
                    command.eventId(), shipment.id(), shipment.status());
        }
        return new CarrierEventResult(command.eventId(), shipment.id(), decision.name(), shipment.status().name());
    }

    private void execute(String operationKey) {
        if (operationKey != null) {
            operationRepository.claim(operationKey, OPERATION_LEASE).ifPresent(this::executeClaimed);
        }
    }

    private void executeClaimed(CarrierOperation operation) {
        try {
            if (operation.type() == Type.REGISTER) {
                executeRegistration(operation);
            } else {
                executeCancellation(operation);
            }
        } catch (RuntimeException failure) {
            log.warn("carrier operation will retry key={} attempt={}",
                    operation.idempotencyKey(), operation.attempts(), failure);
            operationRepository.retry(operation.idempotencyKey(), operation.claimGeneration(),
                    failure.getMessage(), now().plus(RETRY_DELAY));
        }
    }

    private void executeRegistration(CarrierOperation operation) {
        var command = new CarrierGateway.RegistrationCommand(operation.idempotencyKey(), operation.shipmentId());
        var result = operation.attempts() > 1 ? carrierGateway.registration(command) : carrierGateway.register(command);
        if (result.outcome() == CarrierGateway.Outcome.ABSENT) {
            result = carrierGateway.register(command);
        }
        switch (result.outcome()) {
            case CONFIRMED -> confirmRegistration(operation, result);
            case UNKNOWN -> retry(operation, result.message());
            case REJECTED -> requireOperationUpdate(operationRepository.reject(
                    operation.idempotencyKey(), operation.claimGeneration(), result.message(), now()));
            case ABSENT -> retry(operation, "carrier registration is absent after command");
        }
        var shipment = getShipment(operation.shipmentId());
        if (shipment.cancellationStatus() == CancellationStatus.PENDING) {
            execute(CarrierOperation.cancellationKey(shipment.id()));
        }
    }

    private void confirmRegistration(CarrierOperation operation, CarrierGateway.CarrierRegistration registration) {
        transactions.required(() -> {
            var shipment = getShipmentForUpdate(operation.shipmentId());
            var transitioned = shipment.confirmRegistration(registration.carrierCode(), registration.carrierName(),
                    registration.trackingNumber());
            if (transitioned) {
                shipmentRepository.save(shipment);
                shipmentEventRepository.save(ShipmentEvent.occurred("SHIPMENT_REGISTERED", shipment, now()));
            }
            requireOperationUpdate(operationRepository.succeed(
                    operation.idempotencyKey(), operation.claimGeneration(), now()));
            return null;
        });
    }

    private void executeCancellation(CarrierOperation operation) {
        var shipment = getShipment(operation.shipmentId());
        if (shipment.status() == ShipmentStatus.CANCELLED) {
            finishAlreadyCancelled(operation);
            return;
        }
        if (shipment.status() != ShipmentStatus.READY && shipment.status() != ShipmentStatus.AWAITING_PICKUP) {
            requireCancellationAttention(operation, "shipment left before carrier cancellation completed");
            return;
        }
        if (shipment.registrationStatus() == RegistrationStatus.PENDING) {
            execute(CarrierOperation.registrationKey(shipment.id()));
            shipment = getShipment(shipment.id());
            if (shipment.registrationStatus() == RegistrationStatus.PENDING) {
                var registration = operationRepository.find(CarrierOperation.registrationKey(shipment.id()));
                if (registration.isPresent() && registration.get().status() == CarrierOperation.Status.REJECTED) {
                    confirmCancellation(operation);
                    return;
                }
                retry(operation, "carrier registration result is not known yet");
                return;
            }
        }
        if (shipment.trackingNumber() == null) {
            confirmCancellation(operation);
            return;
        }

        var command = new CarrierGateway.CancellationCommand(
                operation.idempotencyKey(), shipment.id(), shipment.trackingNumber());
        var result = operation.attempts() > 1 ? carrierGateway.cancellation(command) : carrierGateway.cancel(command);
        if (result.outcome() == CarrierGateway.Outcome.ABSENT) {
            result = carrierGateway.cancel(command);
        }
        switch (result.outcome()) {
            case CONFIRMED -> confirmCancellation(operation);
            case UNKNOWN -> retry(operation, result.message());
            case REJECTED -> requireCancellationAttention(operation, result.message());
            case ABSENT -> retry(operation, "carrier cancellation is absent after command");
        }
    }

    private void confirmCancellation(CarrierOperation operation) {
        transactions.required(() -> {
            var shipment = getShipmentForUpdate(operation.shipmentId());
            if (shipment.status() != ShipmentStatus.READY && shipment.status() != ShipmentStatus.AWAITING_PICKUP
                    && shipment.status() != ShipmentStatus.CANCELLED) {
                shipment.requireCancellationAttention();
                shipmentRepository.save(shipment);
                requireOperationUpdate(operationRepository.requireAttention(operation.idempotencyKey(),
                        operation.claimGeneration(), "shipment advanced while carrier cancellation was in flight", now()));
                return null;
            }
            shipment.confirmCancellation();
            shipmentRepository.save(shipment);
            requireOperationUpdate(operationRepository.succeed(
                    operation.idempotencyKey(), operation.claimGeneration(), now()));
            return null;
        });
    }

    private void finishAlreadyCancelled(CarrierOperation operation) {
        transactions.required(() -> {
            requireOperationUpdate(operationRepository.succeed(
                    operation.idempotencyKey(), operation.claimGeneration(), now()));
            return null;
        });
    }

    private void requireCancellationAttention(CarrierOperation operation, String message) {
        transactions.required(() -> {
            var shipment = getShipmentForUpdate(operation.shipmentId());
            shipment.requireCancellationAttention();
            shipmentRepository.save(shipment);
            requireOperationUpdate(operationRepository.requireAttention(
                    operation.idempotencyKey(), operation.claimGeneration(), message, now()));
            return null;
        });
    }

    private void retry(CarrierOperation operation, String message) {
        requireOperationUpdate(operationRepository.retry(operation.idempotencyKey(), operation.claimGeneration(),
                message == null ? "carrier result is unknown" : message, now().plus(RETRY_DELAY)));
    }

    private void failIfRejected(String operationKey, String message) {
        if (operationKey == null) {
            return;
        }
        operationRepository.find(operationKey)
                .filter(operation -> operation.status() == CarrierOperation.Status.REJECTED)
                .ifPresent(operation -> {
                    throw DomainException.downstreamError(message);
                });
    }

    private static void requireOperationUpdate(boolean updated) {
        if (!updated) {
            throw new IllegalStateException("carrier operation lease was lost");
        }
    }

    private Shipment getShipment(String shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> DomainException.notFound("shipment not found"));
    }

    private Shipment getShipmentForUpdate(String shipmentId) {
        return shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> DomainException.notFound("shipment not found"));
    }

    private ShipmentDetails sameShipment(
            Shipment shipment,
            String memberId,
            com.impati.commerce.shipping.domain.ShippingModels.Address address
    ) {
        if (!shipment.memberId().equals(memberId) || !shipment.address().equals(address)) {
            throw DomainException.conflict("order already has a different shipment");
        }
        return ShipmentMapper.toDetails(shipment);
    }

    private static boolean sameEvent(CarrierEventRecord existing, CarrierEventCommand command) {
        return existing.carrierCode().equals(command.carrierCode())
                && existing.trackingNumber().equals(command.trackingNumber())
                && existing.eventType().equals(command.type().name())
                && existing.occurredAt().toInstant().equals(command.occurredAt().toInstant());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
