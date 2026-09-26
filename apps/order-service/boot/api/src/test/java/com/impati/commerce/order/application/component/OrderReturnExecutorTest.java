package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.ReturnShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.OrderRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderReturnExecutorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void paidShippingChangeOfMindDeductsOnlyPickupFee() {
        var fixture = fixture(new PriceBreakdown(Money.krw(55_000), Money.krw(3_000), Money.krw(58_000)),
                OffsetDateTime.parse("2026-09-20T03:00:00Z"));

        var result = fixture.executor.request("mem_a", fixture.order.id(), "CHANGE_OF_MIND", null, null, null);

        assertThat(result.refundAmount()).isEqualTo(Money.krw(55_000));
        assertThat(result.status()).isEqualTo("PICKUP_SCHEDULED");
    }

    @Test
    void freeShippingChangeOfMindGetsAFullRefund() {
        var fixture = fixture(new PriceBreakdown(Money.krw(58_000), Money.krw(0), Money.krw(58_000)),
                OffsetDateTime.parse("2026-09-20T03:00:00Z"));

        var result = fixture.executor.request("mem_a", fixture.order.id(), "CHANGE_OF_MIND", null, null, null);

        assertThat(result.refundAmount()).isEqualTo(Money.krw(58_000));
    }

    @Test
    void changeOfMindAfterTheSeventhDayIsRejected() {
        var fixture = fixture(new PriceBreakdown(Money.krw(58_000), Money.krw(0), Money.krw(58_000)),
                OffsetDateTime.parse("2026-09-17T03:00:00Z"));

        assertThatThrownBy(() -> fixture.executor.request(
                "mem_a", fixture.order.id(), "CHANGE_OF_MIND", null, null, null))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("conflict"));
    }

    @Test
    void defectRequiresBothStatutoryWindows() {
        var fixture = fixture(new PriceBreakdown(Money.krw(58_000), Money.krw(0), Money.krw(58_000)),
                OffsetDateTime.parse("2026-07-01T03:00:00Z"));

        assertThatThrownBy(() -> fixture.executor.request("mem_a", fixture.order.id(), "DEFECT_DAMAGE", null,
                LocalDate.parse("2026-08-20"), null)).isInstanceOf(DomainException.class);
    }

    private static Fixture fixture(PriceBreakdown price, OffsetDateTime deliveredAt) {
        var orders = mock(OrderRepository.class);
        var returns = mock(ReturnProgressRepository.class);
        var shipping = mock(ShippingClient.class);
        var changes = mock(OrderChanges.class);
        var address = new Address("adr", "home", "고객", "010", "서울 1", "서울", "01234", true);
        var line = new OrderLine("sku", "prd", "상품", "옵션", 1, price.productAmount());
        var order = Order.restore("ord_return_test_" + price.shippingFee().amount(), "mem_a", List.of(line), price,
                address, OrderStatus.DELIVERED, "pay", "shp", "rsv", LocalDateTime.parse("2026-09-01T00:00:00"), CLOCK);
        when(orders.findByIdAndMemberId(order.id(), "mem_a")).thenReturn(Optional.of(order));
        when(orders.deliveredAt(order.id())).thenReturn(Optional.of(deliveredAt));
        when(returns.findByOrderId(order.id())).thenReturn(Optional.empty());
        var stored = new AtomicReference<ReturnProgress>();
        when(returns.insertIfAbsent(any())).thenAnswer(invocation -> { stored.set(invocation.getArgument(0)); return true; });
        when(returns.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(returns.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(shipping.createReturnShipment(any(), any(), any(), any())).thenAnswer(invocation -> {
            var p = stored.get();
            var a = new AddressResponse(address.id(), address.alias(), address.recipient(), address.phone(),
                    address.line1(), address.city(), address.postalCode(), address.defaultAddress());
            return new ReturnShipmentResponse("rsh", p.id(), order.id(), order.memberId(), a,
                    "AWAITING_PICKUP", "PRIMARY", "택배", "RTN");
        });
        TransactionSection transactions = Runnable::run;
        return new Fixture(order, new OrderReturnExecutor(orders, returns, shipping, changes, transactions, CLOCK));
    }

    private record Fixture(Order order, OrderReturnExecutor executor) { }
}
