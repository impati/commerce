package com.impati.commerce.order.application.model;

/** 주문에 남은 배송지 사본 (PD-0023-R11). */
public record OrderAddress(
        String id,
        String alias,
        String recipient,
        String phone,
        String line1,
        String city,
        String postalCode,
        boolean defaultAddress
) {
}
