package com.impati.commerce.shipping.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.CarrierEventCommand;
import com.impati.commerce.shipping.application.port.in.CarrierEventResult;
import com.impati.commerce.shipping.application.port.in.CarrierRegistrationRecoveryUseCase;
import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import com.impati.commerce.shipping.application.port.out.CarrierEventRecord;
import com.impati.commerce.shipping.application.port.out.CarrierGateway;
import com.impati.commerce.shipping.application.port.out.ShipmentRepository;
import com.impati.commerce.shipping.application.port.out.TransactionSection;
import com.impati.commerce.shipping.domain.ShippingModels.CarrierEventType;
import com.impati.commerce.shipping.domain.ShippingModels.EventDecision;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class ShippingExecutor implements ShippingUseCase, CarrierRegistrationRecoveryUseCase {
    private static final Logger log = LoggerFactory.getLogger(ShippingExecutor.class);

    private final ShipmentRepository shipmentRepository;
    private final CarrierGateway carrierGateway;
    private final TransactionSection transactions;
    private final Clock clock;

    public ShippingExecutor(ShipmentRepository shipmentRepository, CarrierGateway carrierGateway,
            TransactionSection transactions, Clock clock) {
        this.shipmentRepository = shipmentRepository;
        this.carrierGateway = carrierGateway;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ShipmentDetails create(String orderId, String memberId, ShipmentAddress address) {
        var shipmentAddress = ShipmentMapper.toAddress(address);
        var existing = shipmentRepository.findByOrderId(orderId);
        if (existing.isPresent()) return sameShipment(existing.get(), memberId, shipmentAddress);
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
        var shipment = transactions.required(() -> {
            var current = getShipmentForUpdate(shipmentId);
            current.requestRegistration();
            shipmentRepository.save(current);
            return current;
        });
        if (shipment.trackingNumber() != null) return ShipmentMapper.toDetails(shipment);
        try {
            var registration = carrierGateway.register(shipment.id());
            return transactions.required(() -> {
                var current = getShipmentForUpdate(shipmentId);
                current.confirmRegistration(registration.carrierCode(), registration.carrierName(),
                        registration.trackingNumber());
                shipmentRepository.save(current);
                return ShipmentMapper.toDetails(current);
            });
        } catch (RuntimeException failure) {
            // PENDING을 커밋해 Worker가 같은 배송 식별자로 다시 접수한다.
            log.warn("carrier registration remains pending shipment={}", shipmentId, failure);
        }
        return ShipmentMapper.toDetails(getShipment(shipmentId));
    }

    @Override
    public int recoverPendingRegistrations(int batchSize) {
        var recovered = 0;
        for (var shipmentId : shipmentRepository.findPendingRegistrationIds(batchSize)) {
            completePacking(shipmentId);
            recovered++;
        }
        return recovered;
    }

    @Override
    @Transactional
    public ShipmentDetails cancel(String shipmentId) {
        var shipment = getShipmentForUpdate(shipmentId);
        if (shipment.trackingNumber() != null) carrierGateway.cancel(shipment.id(), shipment.trackingNumber());
        shipment.cancel();
        shipmentRepository.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    @Override
    @Transactional
    public CarrierEventResult receive(CarrierEventCommand command) {
        requireText(command.eventId(), "carrier event id");
        requireText(command.carrierCode(), "carrier code");
        requireText(command.trackingNumber(), "tracking number");
        var type = carrierEventType(command.type());
        var shipment = shipmentRepository.findByCarrierAndTrackingForUpdate(
                        command.carrierCode(), command.trackingNumber())
                .orElseThrow(() -> DomainException.notFound("shipment not found for carrier tracking"));
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var received = new CarrierEventRecord(command.eventId(), shipment.id(), command.carrierCode(),
                command.trackingNumber(), type.name(), command.occurredAt(), now, now, "RECEIVED",
                shipment.status().name(), 0);
        if (!shipmentRepository.insertCarrierEventIfAbsent(received)) {
            var existing = shipmentRepository.findCarrierEvent(command.eventId())
                    .orElseThrow(() -> new IllegalStateException("duplicate carrier event disappeared"));
            if (!sameEvent(existing, command, type)) {
                log.warn("carrier event id reused with different content existing={} incoming={}", existing, command);
                return new CarrierEventResult(command.eventId(), existing.shipmentId(), EventDecision.CONFLICT.name(),
                        existing.shipmentStatus());
            }
            shipmentRepository.recordDuplicateCarrierEvent(command.eventId(), now);
            return new CarrierEventResult(command.eventId(), shipment.id(), "DUPLICATE", shipment.status().name());
        }

        var decision = shipment.apply(type, command.occurredAt());
        if (decision == EventDecision.APPLIED) shipmentRepository.save(shipment);
        shipmentRepository.completeCarrierEvent(command.eventId(), decision.name(), shipment.status().name());
        if (decision == EventDecision.CONFLICT) {
            log.warn("carrier event conflicts with shipment terminal state event={} shipment={} status={}",
                    command.eventId(), shipment.id(), shipment.status());
        }
        return new CarrierEventResult(command.eventId(), shipment.id(), decision.name(), shipment.status().name());
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

    private static CarrierEventType carrierEventType(String value) {
        try {
            return CarrierEventType.valueOf(value);
        } catch (RuntimeException failure) {
            throw DomainException.validation("unknown carrier event type");
        }
    }

    private static boolean sameEvent(CarrierEventRecord existing, CarrierEventCommand command, CarrierEventType type) {
        return existing.carrierCode().equals(command.carrierCode())
                && existing.trackingNumber().equals(command.trackingNumber())
                && existing.eventType().equals(type.name())
                && existing.occurredAt().equals(command.occurredAt());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw DomainException.validation(field + " is required");
    }
}
