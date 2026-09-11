package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.CartClient;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OperationalAttention;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutExecutionTest {
    @Test
    void unknownInventoryCommitAfterCaptureIsRetriedForwardWithoutRefunding() {
        var orderId = "ord_commit_unknown";
        var order = Order.create(
                orderId,
                "mem_demo",
                List.of(new OrderLine("sku", "product", "Product", "SKU", 1, Money.krw(1_000))),
                new Address("addr", "home", "Customer", "010", "1 Main", "Seoul", "04524", true));
        order.attachReservation("rsv_demo");
        order.attachPayment("pay_demo");
        order.markPaid();
        order.attachShipment("shp_demo", "TRK-1");
        order.drainPendingEvents();
        var progress = CheckoutProgress.restore(
                orderId, "mem_demo", "key", "a".repeat(64), "card", 1,
                "ORDER_CONFIRMED", "PROCESSING", null, "NONE", null,
                "rsv_demo", "pay_demo", "shp_demo", "TRK-1", null,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), 1);

        var orders = mock(OrderRepository.class);
        var progressRepository = mock(CheckoutProgressRepository.class);
        var changes = mock(CheckoutChanges.class);
        var carts = mock(CartClient.class);
        var inventory = mock(InventoryClient.class);
        var payments = mock(PaymentClient.class);
        var shipping = mock(ShippingClient.class);
        var attention = mock(OperationalAttention.class);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        doThrow(DomainException.outcomeUnknown("inventory commit timed out"))
                .when(inventory).commitReservation("rsv_demo");

        new CheckoutExecution(
                orders, progressRepository, changes, carts, inventory, payments, shipping, attention,
                Clock.systemUTC()).run(progress);

        verify(inventory, times(2)).commitReservation("rsv_demo");
        verify(changes).commit(progress, 1);
        verify(payments, never()).cancelPayment("pay_demo");
        verify(payments, never()).refundPayment("pay_demo");
        verify(shipping, never()).cancelShipment("shp_demo");
    }

    @Test
    void persistenceFailureAfterCaptureWaitsForLeaseRecoveryWithoutCompensating() {
        var orderId = "ord_capture_saved_late";
        var order = Order.create(
                orderId,
                "mem_demo",
                List.of(new OrderLine("sku", "product", "Product", "SKU", 1, Money.krw(1_000))),
                new Address("addr", "home", "Customer", "010", "1 Main", "Seoul", "04524", true));
        order.attachReservation("rsv_demo");
        order.attachPayment("pay_demo");
        var progress = CheckoutProgress.restore(
                orderId, "mem_demo", "key", "a".repeat(64), "card", 1,
                "CAPTURE_PENDING", "PROCESSING", null, "NONE", null,
                "rsv_demo", "pay_demo", "shp_demo", "TRK-1", null,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), 1);

        var orders = mock(OrderRepository.class);
        var progressRepository = mock(CheckoutProgressRepository.class);
        var changes = mock(CheckoutChanges.class);
        var carts = mock(CartClient.class);
        var inventory = mock(InventoryClient.class);
        var payments = mock(PaymentClient.class);
        var shipping = mock(ShippingClient.class);
        var attention = mock(OperationalAttention.class);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(payments.capturePayment("pay_demo")).thenReturn(new PaymentResponse(
                "pay_demo", orderId, "mem_demo", Money.krw(1_000), "CARD", "CAPTURED"));
        doThrow(new CheckoutChanges.PersistenceFailure(orderId, new IllegalStateException("db unavailable")))
                .when(changes).commit(progress, 1);

        new CheckoutExecution(
                orders, progressRepository, changes, carts, inventory, payments, shipping, attention,
                Clock.systemUTC()).run(progress);

        verify(payments).capturePayment("pay_demo");
        verify(payments, never()).payment("pay_demo");
        verify(payments, never()).cancelPayment("pay_demo");
        verify(payments, never()).refundPayment("pay_demo");
        verify(shipping, never()).cancelShipment("shp_demo");
        verify(inventory, never()).releaseReservation("rsv_demo");
        verify(attention, never()).required(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
