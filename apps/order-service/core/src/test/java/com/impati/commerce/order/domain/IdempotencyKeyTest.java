package com.impati.commerce.order.domain;

import com.impati.commerce.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {
    @Test
    void rejectsMissingAndOversizedKeys() {
        assertThatThrownBy(() -> new IdempotencyKey(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new IdempotencyKey("a".repeat(129))).isInstanceOf(DomainException.class);
    }
}
