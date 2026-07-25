package com.impati.commerce.notification.application;

import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.ApiContracts.OutboxEntryResponse;
import com.impati.commerce.notification.domain.NotificationModels.Notification;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다. 도메인은 계약을 모른다.
 */
final class NotificationMapper {
    private NotificationMapper() {
    }

    static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.eventType(),
                notification.memberId(),
                notification.subject(),
                notification.body()
        );
    }

    static OutboxEntryResponse toOutboxEntry(Notification notification) {
        return new OutboxEntryResponse(
                notification.id(),
                notification.channel().name(),
                notification.recipient(),
                notification.subject(),
                notification.body(),
                notification.deliveryStatus().name(),
                notification.attempts()
        );
    }
}
