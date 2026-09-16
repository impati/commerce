package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrderTimeTest {
    /** [PD-0020-R8] 사건은 전이 시각을 보존하고 재발행이 그 시각을 바꾸지 않는다. DB 매핑은 별도 검증한다. */
    @Test
    void recordsEachTransitionTimeWithoutChangingOrderTime() {
        var clock = new MutableClock(Instant.parse("2026-09-16T03:00:00.123456789Z"));
        var order = Order.create("ord_time", "mem_time", List.of(new OrderModels.OrderLine(
                "sku_a", "prd_a", "Product", "Option", 1, Money.krw(10_000))),
                new Address("addr_a", "home", "Recipient", "010", "Line", "City", "Zip", true), clock);
        var createdAt = LocalDateTime.parse("2026-09-16T03:00:00.123456");
        assertThat(order.createdAt()).isEqualTo(createdAt);
        assertThat(order.drainPendingEvents().getFirst().occurredAt()).isEqualTo(createdAt);

        clock.current = Instant.parse("2026-09-16T03:05:00Z");
        order.attachPayment("pay_a");
        order.markPaid();
        var paid = order.drainPendingEvents().getFirst();
        assertThat(paid.occurredAt()).isEqualTo(LocalDateTime.parse("2026-09-16T03:05:00"));
        paid.markFailed("publish error", 3);
        paid.markPublished();
        assertThat(paid.occurredAt()).isEqualTo(LocalDateTime.parse("2026-09-16T03:05:00"));

        clock.current = Instant.parse("2026-09-16T03:10:00Z");
        order.attachShipment("shp_a", "TRK-a");
        assertThat(order.drainPendingEvents().getFirst().occurredAt())
                .isEqualTo(LocalDateTime.parse("2026-09-16T03:10:00"));
        assertThat(order.createdAt()).isEqualTo(createdAt);
    }

    private static final class MutableClock extends Clock {
        Instant current;
        MutableClock(Instant current) { this.current = current; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
