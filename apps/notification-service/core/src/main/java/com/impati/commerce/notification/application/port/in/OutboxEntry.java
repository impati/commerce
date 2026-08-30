package com.impati.commerce.notification.application.port.in;

/** 아웃박스의 한 항목. 발송 상태와 시도 횟수가 여기에만 있다 (PD-0009-R4). */
public record OutboxEntry(
        String id,
        String channel,
        String recipient,
        String subject,
        String body,
        String deliveryStatus,
        int attempts
) {
}
