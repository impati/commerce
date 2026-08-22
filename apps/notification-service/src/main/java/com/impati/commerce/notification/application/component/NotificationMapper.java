package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.NotificationDetails;
import com.impati.commerce.notification.application.port.in.OutboxEntry;
import com.impati.commerce.notification.domain.NotificationModels.Notification;

/**
 * 도메인 모델을 유스케이스 결과로 옮긴다. 도메인은 결과 타입을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 볼 수 있으면 도메인 객체를 손에 넣어야 부를 수
 * 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 */
final class NotificationMapper {
    private NotificationMapper() {
    }

    static NotificationDetails toDetails(Notification notification) {
        return new NotificationDetails(
                notification.id(),
                notification.eventType(),
                notification.memberId(),
                notification.subject(),
                notification.body()
        );
    }

    static OutboxEntry toOutboxEntry(Notification notification) {
        return new OutboxEntry(
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
