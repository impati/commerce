package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.order.application.port.out.NotificationClient;
import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 사건 발행의 로컬 대역 (ADR-0012).
 *
 * <p>구독자가 알림 서비스 하나이므로 브로커 없이 직접 부른다. 인프라는 대역으로 두고 포트는
 * 완성 상태로 만든다는 이 저장소의 방침에 따른 것이며, 브로커가 들어올 때 갈아끼우는 것이
 * 이 클래스다 — 응용 계층과 도메인은 바뀌지 않는다.
 *
 * <p><b>구독자의 판단이 여기 섞여 있다.</b> 사건을 알림 문구로 옮기는 것과, 알림이 없는 사건을
 * 흘려보내는 것은 원래 소비자의 일이다. 대역이 발행자와 소비자를 겸하기 때문에 여기 있으며,
 * 브로커가 들어오면 이 판단은 알림 서비스로 넘어간다.
 *
 * <p>{@link OrderEvent#partitionKey()}를 쓰지 않는다. 직접 호출은 순서가 호출 순서로 정해지기
 * 때문이며, 브로커에서 그 값이 순서 보장의 단위가 된다.
 */
@Component
public class NotifyingOrderEventPublisher implements OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotifyingOrderEventPublisher.class);

    private final NotificationClient notificationClient;

    public NotifyingOrderEventPublisher(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @Override
    public void publish(OrderEvent event) {
        var notification = OrderEventNotifications.from(event);
        if (notification.isEmpty()) {
            // 이 구독자가 관심 없는 사건이다. 소비자가 자기와 무관한 사건을 흘려보내는 것은
            // 정상이며, 발행 자체는 성공한 것이므로 종단시킨다.
            log.debug("no notification for order event type={} id={}", event.type(), event.id());
            return;
        }
        notificationClient.notify(notification.orElseThrow());
    }

    /**
     * 사건을 알림 문구로 옮긴다.
     *
     * <p>사건에는 사실만 있고 문구는 여기서 만든다. 사실에 문구를 박으면 두 번째 소비자가
     * 쓸 수 없기 때문이다 (ADR-0012).
     *
     * <p>멱등 키는 사건 행 id다. 재시도는 같은 사건이므로 키가 그대로이고, 새 전이는 새 사건이라
     * 새 키다. 그래서 중복 판정에 유효 기간을 두지 않아도 정상적인 알림이 막히지 않는다.
     */
    static final class OrderEventNotifications {
        private OrderEventNotifications() {
        }

        static Optional<NotificationEventRequest> from(OrderEvent event) {
            return switch (event.type()) {
                case ORDER_PAID -> Optional.of(notification(event, "OrderPaid",
                        "Order paid",
                        "Order " + event.orderId() + " has been paid."));
                case SHIPMENT_CREATED -> Optional.of(notification(event, "ShipmentCreated",
                        "Shipment ready",
                        "Tracking number: " + event.payload().getOrDefault("trackingNumber", "")));
                case ORDER_DELIVERED -> Optional.of(notification(event, "OrderDelivered",
                        "Order delivered",
                        "Order " + event.orderId() + " was delivered."));
                case ORDER_CANCELLED -> Optional.of(notification(event, "OrderCancelled",
                        "Order cancelled",
                        "Order " + event.orderId() + " was cancelled: "
                                + event.payload().getOrDefault("reason", "")));
                // 주문 접수는 아직 알리지 않는다. 사건을 남기는 기준은 소비자가 아니라 상태
                // 전이이므로, 소비자가 없는 사건이 있는 것은 정상이다.
                case ORDER_CREATED -> Optional.empty();
            };
        }

        private static NotificationEventRequest notification(
                OrderEvent event, String eventType, String subject, String body) {
            return new NotificationEventRequest(eventType, event.memberId(), subject, body, event.id());
        }
    }
}
