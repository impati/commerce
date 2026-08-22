package com.impati.commerce.member.application.port.in;

/** 새로 추가할 주소. 식별자는 저장하면서 발급한다. */
public record NewAddress(
        String alias,
        String recipient,
        String phone,
        String line1,
        String city,
        String postalCode,
        boolean defaultAddress
) {
}
