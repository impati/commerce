package com.impati.commerce.member.application.port.in;

/** 회원 주소록의 한 항목. */
public record MemberAddress(
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
