package com.impati.commerce.order.application.port.in;

/** 주문에 남은 배송지 사본 (PD-0003-R6). */
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
