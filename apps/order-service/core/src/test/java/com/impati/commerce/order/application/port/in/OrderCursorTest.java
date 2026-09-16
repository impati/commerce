package com.impati.commerce.order.application.port.in;

import com.impati.commerce.common.DomainException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCursorTest {
    @Test
    void roundTripsBothComponentsAndMicroseconds() {
        var cursor = new OrderCursor(LocalDateTime.parse("2026-09-16T12:00:00.123456"), "ord_same_time_b");
        assertThat(OrderCursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThat(cursor.encode()).doesNotContain("+", "/", "=");
        assertThat(OrderCursor.decode(null)).isNull();
    }

    @Test
    void rejectsMalformedAndUnboundedCursors() {
        for (var cursor : new String[] { "", "garbage", "a".repeat(513), "!not-base64!" }) {
            assertThatThrownBy(() -> OrderCursor.decode(cursor)).isInstanceOf(DomainException.class);
        }
        assertThatThrownBy(() -> new OrderCursor(LocalDateTime.parse("2026-09-16T12:00:00.123456789"), "ord_a"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new OrderCursor(LocalDateTime.parse("2026-09-16T12:00"), "ord|a"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void acceptsOnlyBoundedPositivePageSizes() {
        assertThat(OrderQueryKey.of("mem_a", null, 100).size()).isEqualTo(100);
        for (var size : new int[] { -1, 0, 101 }) {
            assertThatThrownBy(() -> OrderQueryKey.of("mem_a", null, size)).isInstanceOf(DomainException.class);
        }
    }
}
