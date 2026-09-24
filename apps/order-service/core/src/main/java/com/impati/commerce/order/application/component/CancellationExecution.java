package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OperationalAttention;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CancellationProgress;
import com.impati.commerce.order.domain.CancellationProgress.Stage;
import com.impati.commerce.order.domain.OrderModels.Order;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** API와 워커가 공유하는 고객 주문 취소 단계 실행기 (ADR-0027). */
@Component
@ConditionalOnBean({InventoryClient.class, PaymentClient.class, ShippingClient.class})
public class CancellationExecution {
    private static final Logger log = LoggerFactory.getLogger(CancellationExecution.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final OrderRepository orderRepository;
    private final CancellationChanges changes;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final ShippingClient shippingClient;
    private final OperationalAttention operationalAttention;
    private final Clock clock;

    public CancellationExecution(
            OrderRepository orderRepository,
            CancellationChanges changes,
            InventoryClient inventoryClient,
            PaymentClient paymentClient,
            ShippingClient shippingClient,
            OperationalAttention operationalAttention,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.changes = changes;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.shippingClient = shippingClient;
        this.operationalAttention = operationalAttention;
        this.clock = clock;
    }

    public void run(CancellationProgress progress) {
        try {
            while (true) {
                switch (progress.stage()) {
                    case REQUESTED -> cancelShipment(progress);
                    case SHIPMENT_CANCELLED -> refundPayment(progress);
                    case PAYMENT_REFUNDED -> restoreInventory(progress);
                    case INVENTORY_RESTORED -> complete(progress);
                    case COMPLETED, REJECTED, ATTENTION_REQUIRED -> { return; }
                }
            }
        } catch (CancellationChanges.LeaseLostException ignored) {
            log.info("cancellation execution stopped after lease loss order={}", progress.orderId());
        } catch (CancellationChanges.PersistenceFailure failure) {
            log.warn("cancellation execution stopped after persistence failure order={}", progress.orderId(), failure);
        } catch (RuntimeException failure) {
            handleFailure(progress, failure);
        }
    }

    private void cancelShipment(CancellationProgress progress) {
        var order = order(progress.orderId());
        var shipment = shippingClient.shipmentForOrder(progress.orderId())
                .orElseThrow(() -> new IllegalStateException("successful order has no shipment"));
        ensureShipmentMatches(order, shipment);
        switch (shipmentState(shipment)) {
            case READY, AWAITING_PICKUP -> {
                try {
                    shipment = shippingClient.cancelShipment(shipment.id());
                } catch (DomainException failure) {
                    if (!failure.code().equals("conflict") && !failure.code().equals("outcome_unknown")) throw failure;
                    shipment = shippingClient.shipmentForOrder(progress.orderId()).orElseThrow(() -> failure);
                }
                ensureShipmentMatches(order, shipment);
                settleShipment(progress, order, shipment);
            }
            case CANCELLED -> settleShipment(progress, order, shipment);
            case IN_TRANSIT, DELIVERED, RETURNING, RETURNED -> reject(progress, "shipment already left");
        }
    }

    private void settleShipment(CancellationProgress progress, Order order, ShipmentResponse shipment) {
        switch (shipmentState(shipment)) {
            case CANCELLED -> {
                order.requestCancellation();
                progress.advance(Stage.SHIPMENT_CANCELLED);
                changes.commit(order, progress, progress.leaseGeneration());
            }
            case IN_TRANSIT, DELIVERED, RETURNING, RETURNED -> reject(progress, "shipment already left");
            case READY, AWAITING_PICKUP -> throw DomainException.unavailable("shipment cancellation is not settled");
        }
    }

    private void refundPayment(CancellationProgress progress) {
        var order = order(progress.orderId());
        var payment = paymentClient.paymentForOrder(progress.orderId())
                .orElseThrow(() -> new IllegalStateException("successful order has no payment"));
        ensurePaymentMatches(order, payment);
        switch (payment.status()) {
            case "CAPTURED" -> {
                try {
                    payment = paymentClient.refundPayment(payment.id());
                } catch (DomainException failure) {
                    if (!failure.code().equals("outcome_unknown")) throw failure;
                    payment = paymentClient.payment(payment.id());
                }
                if (!payment.status().equals("REFUNDED")) {
                    throw DomainException.unavailable("payment refund is not settled");
                }
            }
            case "REFUNDED" -> { }
            default -> throw new IllegalStateException("cancellable order payment is " + payment.status());
        }
        progress.advance(Stage.PAYMENT_REFUNDED);
        changes.commit(progress, progress.leaseGeneration());
    }

    private void restoreInventory(CancellationProgress progress) {
        var order = order(progress.orderId());
        var reservation = inventoryClient.reservationForOrder(progress.orderId())
                .orElseThrow(() -> new IllegalStateException("successful order has no inventory reservation"));
        ensureReservationMatches(order, reservation);
        switch (reservation.status()) {
            case "COMMITTED" -> {
                try {
                    inventoryClient.restoreReservation(reservation.id());
                } catch (DomainException failure) {
                    if (!failure.code().equals("outcome_unknown")) throw failure;
                    reservation = inventoryClient.reservationForOrder(progress.orderId()).orElseThrow(() -> failure);
                    ensureReservationMatches(order, reservation);
                    if (!reservation.status().equals("RESTORED")) throw failure;
                }
            }
            case "RESTORED" -> { }
            default -> throw new IllegalStateException("cancellable order reservation is " + reservation.status());
        }
        progress.advance(Stage.INVENTORY_RESTORED);
        changes.commit(progress, progress.leaseGeneration());
    }

    private void complete(CancellationProgress progress) {
        var order = order(progress.orderId());
        order.cancelByCustomer();
        progress.complete();
        changes.commit(order, progress, progress.leaseGeneration());
    }

    private void reject(CancellationProgress progress, String reason) {
        progress.reject("CANCELLATION_NOT_ALLOWED", reason);
        changes.commit(progress, progress.leaseGeneration());
    }

    private void handleFailure(CancellationProgress progress, RuntimeException failure) {
        if (failure instanceof CancellationChanges.LeaseLostException) return;
        if (failure instanceof DomainException domain
                && (domain.code().equals("service_unavailable") || domain.code().equals("outcome_unknown"))) {
            progress.retryLater(failure.getMessage(), now().plus(RETRY_DELAY));
            changes.commit(progress, progress.leaseGeneration());
            return;
        }
        var stage = progress.stage().name();
        progress.attention(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        changes.commit(progress, progress.leaseGeneration());
        operationalAttention.required(progress.orderId(), "CANCELLATION_" + stage, progress.lastError());
    }

    private static void ensurePaymentMatches(Order order, PaymentResponse payment) {
        if (!payment.orderId().equals(order.id())
                || !payment.memberId().equals(order.memberId())
                || !payment.amount().equals(order.total())) {
            throw new IllegalStateException("payment does not match cancellation order");
        }
    }

    private static void ensureShipmentMatches(Order order, ShipmentResponse shipment) {
        if (!Objects.equals(shipment.id(), order.shipmentId())
                || !Objects.equals(shipment.orderId(), order.id())) {
            throw new IllegalStateException("shipment does not match cancellation order");
        }
    }

    private static void ensureReservationMatches(Order order, ReservationResponse reservation) {
        if (!Objects.equals(reservation.id(), order.inventoryReservationId())
                || !Objects.equals(reservation.orderId(), order.id())) {
            throw new IllegalStateException("inventory reservation does not match cancellation order");
        }
    }

    private Order order(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("cancellation order disappeared: " + orderId));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static ShipmentState shipmentState(ShipmentResponse shipment) {
        try {
            return ShipmentState.valueOf(shipment.status());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("unknown shipment status " + shipment.status(), failure);
        }
    }

    private enum ShipmentState {
        READY,
        AWAITING_PICKUP,
        IN_TRANSIT,
        DELIVERED,
        RETURNING,
        RETURNED,
        CANCELLED
    }
}
