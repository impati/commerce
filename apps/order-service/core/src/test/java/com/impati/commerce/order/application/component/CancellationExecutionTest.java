package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OperationalAttention;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CancellationProgress;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.order.domain.OrderModels.OrderStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CancellationExecutionTest {

    /** [PD-0024-R4~R7][PD-0024-R12] 배송·전액 환불·재고 복원 뒤에만 주문 취소가 완료된다. */
    @Test
    void completesAllCancellationStepsInOrder() {
        var fixture = fixture("ord_cancel_complete");
        when(fixture.shipping.shipmentForOrder(fixture.order.id())).thenReturn(Optional.of(shipment(fixture, "READY")));
        when(fixture.shipping.cancelShipment("shp_demo")).thenReturn(shipment(fixture, "CANCELLED"));
        when(fixture.payments.paymentForOrder(fixture.order.id())).thenReturn(Optional.of(payment(fixture, "CAPTURED")));
        when(fixture.payments.refundPayment("pay_demo")).thenReturn(payment(fixture, "REFUNDED"));
        when(fixture.inventory.reservationForOrder(fixture.order.id()))
                .thenReturn(Optional.of(reservation(fixture, "COMMITTED")));

        fixture.execution.run(fixture.progress);

        assertThat(fixture.progress.stage()).isEqualTo(CancellationProgress.Stage.COMPLETED);
        assertThat(fixture.order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(fixture.order.drainPendingEvents()).extracting(event -> event.type())
                .containsExactly(OrderEventType.ORDER_CANCELLATION_REQUESTED, OrderEventType.ORDER_CANCELLED);
        verify(fixture.shipping).cancelShipment("shp_demo");
        verify(fixture.payments).refundPayment("pay_demo");
        verify(fixture.inventory).restoreReservation("rsv_demo");
    }

    /** [PD-0024-R7] 저장된 배송 취소 다음 단계에서 중단되면 배송 취소를 반복하지 않고 재개한다. */
    @Test
    void resumesAfterShipmentCancellationWithoutCancellingShipmentAgain() {
        var fixture = fixture("ord_cancel_resume");
        when(fixture.shipping.shipmentForOrder(fixture.order.id())).thenReturn(Optional.of(shipment(fixture, "READY")));
        when(fixture.shipping.cancelShipment("shp_demo")).thenReturn(shipment(fixture, "CANCELLED"));
        when(fixture.payments.paymentForOrder(fixture.order.id()))
                .thenThrow(DomainException.unavailable("payment unavailable"))
                .thenReturn(Optional.of(payment(fixture, "CAPTURED")));
        when(fixture.payments.refundPayment("pay_demo")).thenReturn(payment(fixture, "REFUNDED"));
        when(fixture.inventory.reservationForOrder(fixture.order.id()))
                .thenReturn(Optional.of(reservation(fixture, "COMMITTED")));

        fixture.execution.run(fixture.progress);
        assertThat(fixture.progress.stage()).isEqualTo(CancellationProgress.Stage.SHIPMENT_CANCELLED);
        assertThat(fixture.progress.nextAttemptAt()).isNotNull();

        fixture.execution.run(fixture.progress);

        assertThat(fixture.progress.stage()).isEqualTo(CancellationProgress.Stage.COMPLETED);
        verify(fixture.shipping, times(1)).cancelShipment("shp_demo");
        verify(fixture.payments, times(1)).refundPayment("pay_demo");
    }

    /** [PD-0024-R3] 출고가 먼저 확정되면 취소를 거절하고 돈과 재고를 건드리지 않는다. */
    @Test
    void rejectsCancellationWhenShipmentAlreadyLeft() {
        var fixture = fixture("ord_cancel_rejected");
        when(fixture.shipping.shipmentForOrder(fixture.order.id()))
                .thenReturn(Optional.of(shipment(fixture, "IN_TRANSIT")));

        fixture.execution.run(fixture.progress);

        assertThat(fixture.progress.stage()).isEqualTo(CancellationProgress.Stage.REJECTED);
        assertThat(fixture.progress.failureCode()).isEqualTo("CANCELLATION_NOT_ALLOWED");
        assertThat(fixture.order.status()).isEqualTo(OrderStatus.FULFILLING);
        assertThat(fixture.order.drainPendingEvents()).isEmpty();
        verify(fixture.payments, never()).refundPayment("pay_demo");
        verify(fixture.inventory, never()).restoreReservation("rsv_demo");
    }

    /** [PD-0024-R10] 환불 응답을 잃으면 결제 상태를 확인해 이중 환불 없이 계속한다. */
    @Test
    void confirmsUnknownRefundBeforeContinuing() {
        var fixture = fixture("ord_cancel_refund_unknown");
        fixture.progress.advance(CancellationProgress.Stage.SHIPMENT_CANCELLED);
        when(fixture.payments.paymentForOrder(fixture.order.id())).thenReturn(Optional.of(payment(fixture, "CAPTURED")));
        doThrow(DomainException.outcomeUnknown("refund timed out"))
                .when(fixture.payments).refundPayment("pay_demo");
        when(fixture.payments.payment("pay_demo")).thenReturn(payment(fixture, "REFUNDED"));
        when(fixture.inventory.reservationForOrder(fixture.order.id()))
                .thenReturn(Optional.of(reservation(fixture, "RESTORED")));

        fixture.execution.run(fixture.progress);

        assertThat(fixture.progress.stage()).isEqualTo(CancellationProgress.Stage.COMPLETED);
        verify(fixture.payments, times(1)).refundPayment("pay_demo");
        verify(fixture.payments, times(1)).payment("pay_demo");
        verify(fixture.inventory, never()).restoreReservation("rsv_demo");
    }

    @Test
    void stopsBeforeCancellingAShipmentThatBelongsToAnotherOrder() {
        var fixture = fixture("ord_cancel_wrong_shipment");
        when(fixture.shipping.shipmentForOrder(fixture.order.id())).thenReturn(Optional.of(
                new ShipmentResponse("shp_other", "ord_other", "mem_demo", null, "READY", "TRK-other")));

        fixture.execution.run(fixture.progress);

        assertThat(fixture.progress.stage()).isEqualTo(CancellationProgress.Stage.ATTENTION_REQUIRED);
        verify(fixture.shipping, never()).cancelShipment("shp_other");
        verify(fixture.attention).required(fixture.order.id(), "CANCELLATION_REQUESTED",
                "IllegalStateException: shipment does not match cancellation order");
    }

    @Test
    void stopsBeforeRestoringAReservationThatBelongsToAnotherOrder() {
        var fixture = fixture("ord_cancel_wrong_reservation");
        fixture.progress.advance(CancellationProgress.Stage.PAYMENT_REFUNDED);
        when(fixture.inventory.reservationForOrder(fixture.order.id())).thenReturn(Optional.of(
                new ReservationResponse("rsv_other", "ord_other", "COMMITTED",
                        List.of(new ReservationLine("sku_demo", 2)))));

        fixture.execution.run(fixture.progress);

        assertThat(fixture.progress.stage()).isEqualTo(CancellationProgress.Stage.ATTENTION_REQUIRED);
        verify(fixture.inventory, never()).restoreReservation("rsv_other");
        verify(fixture.attention).required(fixture.order.id(), "CANCELLATION_PAYMENT_REFUNDED",
                "IllegalStateException: inventory reservation does not match cancellation order");
    }

    private static Fixture fixture(String orderId) {
        var order = Order.create(orderId, "mem_demo",
                List.of(new OrderLine("sku_demo", "prd_demo", "Product", "Option", 2, Money.krw(10_000))),
                new Address("addr", "home", "Customer", "010", "1 Main", "Seoul", "04524", true),
                LocalDateTime.now());
        order.attachReservation("rsv_demo");
        order.attachPayment("pay_demo");
        order.markPaid();
        order.attachShipment("shp_demo", "TRK-1");
        order.drainPendingEvents();

        var orders = mock(OrderRepository.class);
        var changes = mock(CancellationChanges.class);
        var inventory = mock(InventoryClient.class);
        var payments = mock(PaymentClient.class);
        var shipping = mock(ShippingClient.class);
        var attention = mock(OperationalAttention.class);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        var progress = new CancellationProgress(orderId, "mem_demo");
        var execution = new CancellationExecution(orders, changes, inventory, payments, shipping,
                attention, Clock.systemUTC());
        return new Fixture(order, progress, execution, inventory, payments, shipping, attention);
    }

    private static ShipmentResponse shipment(Fixture fixture, String status) {
        return new ShipmentResponse("shp_demo", fixture.order.id(), "mem_demo", null, status, "TRK-1");
    }

    private static PaymentResponse payment(Fixture fixture, String status) {
        return new PaymentResponse("pay_demo", fixture.order.id(), "mem_demo", fixture.order.total(), "CARD", status);
    }

    private static ReservationResponse reservation(Fixture fixture, String status) {
        return new ReservationResponse("rsv_demo", fixture.order.id(), status,
                List.of(new ReservationLine("sku_demo", 2)));
    }

    private record Fixture(
            Order order,
            CancellationProgress progress,
            CancellationExecution execution,
            InventoryClient inventory,
            PaymentClient payments,
            ShippingClient shipping,
            OperationalAttention attention
    ) {
    }
}
