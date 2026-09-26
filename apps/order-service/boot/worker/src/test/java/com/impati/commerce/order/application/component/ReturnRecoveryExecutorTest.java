package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.RefundPaymentResponse;
import com.impati.commerce.common.ApiContracts.ReturnInventoryResponse;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ReturnProgressRepository;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.application.port.out.TransactionSection;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.order.domain.OrderModels.OrderStatus;
import com.impati.commerce.order.domain.PriceBreakdown;
import com.impati.commerce.order.domain.ReturnProgress;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnRecoveryExecutorTest {
    @Test
    void uninspectedReturnAfterSeventyTwoHoursIsQuarantinedAndCompleted() {
        var clock = Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC);
        var address = new Address("adr", "home", "고객", "010", "서울", "서울", "01234", true);
        var order = Order.restore("ord_return_recovery", "mem_a",
                List.of(new OrderLine("sku", "prd", "상품", "옵션", 1, Money.krw(58_000))),
                PriceBreakdown.of(Money.krw(58_000), Money.krw(0)), address, OrderStatus.DELIVERED,
                "pay", "shp", "rsv", LocalDateTime.parse("2026-09-20T00:00:00"), clock);
        var progress = ReturnProgress.failedDelivery("ret_recovery", order.id(), order.memberId(), order.total(),
                address, OffsetDateTime.parse("2026-09-22T03:00:00Z"));
        var returns = mock(ReturnProgressRepository.class); var orders = mock(OrderRepository.class);
        var shipping = mock(ShippingClient.class); var payments = mock(PaymentClient.class);
        var inventory = mock(InventoryClient.class); var changes = mock(OrderChanges.class);
        when(returns.findRecoverable(any(), any(Integer.class))).thenReturn(List.of(progress));
        when(returns.findById(progress.id())).thenReturn(Optional.of(progress));
        when(returns.findByIdForUpdate(progress.id())).thenReturn(Optional.of(progress));
        when(orders.findById(order.id())).thenReturn(Optional.of(order));
        when(payments.refundReturn("pay", progress.id(), progress.refundAmount()))
                .thenReturn(new RefundPaymentResponse(progress.id(), "pay", progress.refundAmount(), "SUCCEEDED"));
        when(inventory.processReturn("rsv", progress.id(), order.memberId(), "NON_SALEABLE", "UNINSPECTED_TIMEOUT"))
                .thenReturn(new ReturnInventoryResponse(progress.id(), "rsv", order.memberId(),
                        "NON_SALEABLE", "UNINSPECTED_TIMEOUT"));
        TransactionSection transactions = Runnable::run;
        var executor = new ReturnRecoveryExecutor(returns, orders, shipping, payments, inventory,
                transactions, changes, clock);

        executor.recover(20);

        assertThat(progress.status()).isEqualTo(ReturnProgress.Status.COMPLETED);
        assertThat(progress.disposition()).isEqualTo("NON_SALEABLE");
        assertThat(order.status()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void lostRefundResponseIsReconciledByTheStableReturnId() {
        var clock = Clock.fixed(Instant.parse("2026-09-25T04:00:00Z"), ZoneOffset.UTC);
        var address = new Address("adr", "home", "고객", "010", "서울", "서울", "01234", true);
        var order = Order.restore("ord_refund_reconcile", "mem_a",
                List.of(new OrderLine("sku", "prd", "상품", "옵션", 1, Money.krw(58_000))),
                PriceBreakdown.of(Money.krw(58_000), Money.krw(0)), address, OrderStatus.DELIVERED,
                "pay", "shp", "rsv", LocalDateTime.parse("2026-09-20T00:00:00"), clock);
        var progress = ReturnProgress.failedDelivery("ret_refund_reconcile", order.id(), order.memberId(),
                order.total(), address, OffsetDateTime.parse("2026-09-25T03:00:00Z"));
        progress.inspect("SALEABLE", "NORMAL");
        var returns = mock(ReturnProgressRepository.class); var orders = mock(OrderRepository.class);
        var shipping = mock(ShippingClient.class); var payments = mock(PaymentClient.class);
        var inventory = mock(InventoryClient.class); var changes = mock(OrderChanges.class);
        when(returns.findRecoverable(any(), any(Integer.class))).thenReturn(List.of(progress));
        when(returns.findById(progress.id())).thenReturn(Optional.of(progress));
        when(returns.findByIdForUpdate(progress.id())).thenReturn(Optional.of(progress));
        when(orders.findById(order.id())).thenReturn(Optional.of(order));
        when(payments.refundReturn("pay", progress.id(), progress.refundAmount()))
                .thenThrow(new IllegalStateException("response lost"));
        when(payments.returnRefund(progress.id())).thenReturn(Optional.of(
                new RefundPaymentResponse(progress.id(), "pay", progress.refundAmount(), "SUCCEEDED")));
        when(inventory.processReturn("rsv", progress.id(), order.memberId(), "SALEABLE", "NORMAL"))
                .thenReturn(new ReturnInventoryResponse(progress.id(), "rsv", order.memberId(), "SALEABLE", "NORMAL"));
        var executor = new ReturnRecoveryExecutor(returns, orders, shipping, payments, inventory,
                Runnable::run, changes, clock);

        executor.recover(20);

        assertThat(progress.status()).isEqualTo(ReturnProgress.Status.COMPLETED);
        assertThat(progress.refundStatus()).isEqualTo(ReturnProgress.WorkStatus.SUCCEEDED);
        assertThat(order.status()).isEqualTo(OrderStatus.RETURNED);
    }
}
