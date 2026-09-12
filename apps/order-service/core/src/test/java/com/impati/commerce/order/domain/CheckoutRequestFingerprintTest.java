package com.impati.commerce.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestFingerprintTest {
    @Test
    void preservesFieldBoundaries() {
        var first = CheckoutRequestFingerprint.from("a\u0000b", "c");
        var second = CheckoutRequestFingerprint.from("a", "b\u0000c");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void distinguishesNullFromItsTextRepresentation() {
        var omitted = CheckoutRequestFingerprint.from("card", null);
        var literal = CheckoutRequestFingerprint.from("card", "null");

        assertThat(omitted).isNotEqualTo(literal);
    }
}
