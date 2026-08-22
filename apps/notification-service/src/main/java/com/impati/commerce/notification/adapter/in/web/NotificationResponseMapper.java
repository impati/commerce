package com.impati.commerce.notification.adapter.in.web;

import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.ApiContracts.OutboxEntryResponse;
import com.impati.commerce.notification.application.port.in.NotificationDetails;
import com.impati.commerce.notification.application.port.in.OutboxEntry;

/**
 * 유스케이스 결과를 서비스 간 HTTP 계약으로 옮긴다.
 *
 * <p>계약이 <b>서비스 간</b>의 것이므로 어댑터가 안다. 응용 계층이 알면 인바운드 어댑터가
 * 늘어날 때마다 응용이 바뀐다 — 이 서비스는 스케줄러도 응용을 부르므로 실제로 둘이다.
 */
final class NotificationResponseMapper {
    private NotificationResponseMapper() {
    }

    static NotificationResponse from(NotificationDetails notification) {
        return new NotificationResponse(
                notification.id(),
                notification.eventType(),
                notification.memberId(),
                notification.subject(),
                notification.body()
        );
    }

    static OutboxEntryResponse from(OutboxEntry entry) {
        return new OutboxEntryResponse(
                entry.id(),
                entry.channel(),
                entry.recipient(),
                entry.subject(),
                entry.body(),
                entry.deliveryStatus(),
                entry.attempts()
        );
    }
}
