package com.impati.commerce.notification.adapter.in.consumer;

import com.impati.commerce.common.ApiContracts.OrderEventMessage;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 주문 사건을 받아 알림으로 적는다 (ADR-0016).
 *
 * <p>컨트롤러와 스케줄러가 그렇듯 이것도 애플리케이션을 바깥에서 부르는 진입점이므로
 * {@code adapter/in} 아래에 있다.
 *
 * <p><b>무엇을 알릴지 정하는 것이 여기다.</b> 사건은 사실만 담고 문구는 소비자가 만든다. 예전에는
 * order-service의 발행 어댑터가 이 판단을 갖고 있었는데, 그것은 대역이 발행자와 소비자를 겸했기
 * 때문이었다. 브로커가 들어오면서 제자리를 찾았다.
 *
 * <p><b>실패하면 오프셋을 올리지 않는다.</b> 예외를 그대로 던져 스프링 카프카가 재시도하게 하고,
 * 성공할 때까지 그 파티션이 멈춘다. 건너뛰면 조용한 유실이고, 다른 토픽으로 흘려보내면 그 주문의
 * 순서가 거기서 깨진다 — 릴레이가 순서를 지키려고 한 일이 무의미해진다. 막혀서 드러나는 편이
 * 낫다.
 */
@Component
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final NotificationUseCase notificationUseCase;

    public OrderEventConsumer(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    @KafkaListener(topics = "${commerce.kafka.order-events-topic}")
    public void onOrderEvent(OrderEventMessage message) {
        var notification = OrderEventNotifications.from(message);
        if (notification.isEmpty()) {
            // 이 소비자가 관심 없는 사건이다. 사건을 남기는 기준은 소비자가 아니라 상태 전이이므로
            // 소비자가 없는 사건이 있는 것은 정상이며, 오프셋은 올라간다.
            log.debug("no notification for order event type={} id={}", message.type(), message.eventId());
            return;
        }
        var recorded = notification.orElseThrow();
        notificationUseCase.record(
                recorded.eventType(),
                message.memberId(),
                recorded.subject(),
                recorded.body(),
                message.eventId());
    }

    /**
     * 사건을 알림 문구로 옮긴다.
     *
     * <p>멱등 키는 사건 id다. 브로커가 at-least-once이므로 같은 사건이 두 번 도착할 수 있고,
     * 재시도는 같은 사건이라 키가 그대로다. 새 전이는 새 사건이라 새 키이므로 중복 판정에 유효
     * 기간을 두지 않아도 정상적인 알림이 막히지 않는다.
     */
    static final class OrderEventNotifications {
        private OrderEventNotifications() {
        }

        record Wording(String eventType, String subject, String body) {
        }

        static Optional<Wording> from(OrderEventMessage message) {
            return switch (message.type()) {
                case "ORDER_PAID" -> Optional.of(new Wording("OrderPaid",
                        "Order paid",
                        "Order " + message.orderId() + " has been paid."));
                case "SHIPMENT_REGISTERED" -> Optional.of(new Wording("ShipmentCreated",
                        "Shipment ready",
                        "Tracking number: " + payload(message, "trackingNumber")));
                case "ORDER_DELIVERED" -> Optional.of(new Wording("OrderDelivered",
                        "Order delivered",
                        "Order " + message.orderId() + " was delivered."));
                case "CHECKOUT_FAILED" -> Optional.of(new Wording("CheckoutFailed",
                        "Purchase failed",
                        "Purchase for order " + message.orderId() + " could not be completed."));
                case "ORDER_CANCELLED" -> Optional.of(new Wording("OrderCancelled",
                        "Order cancellation completed",
                        "Order " + message.orderId() + " was cancelled and refunded."));
                // 주문 접수는 아직 알리지 않는다.
                //
                // 모르는 종류도 여기로 온다. 발행자가 새 사건을 추가하는 것이 소비자를 깨뜨리면
                // 안 되므로 흘려보낸다 — 계약이 더하는 방향으로만 안전하다는 것과 같은 이유다.
                default -> Optional.empty();
            };
        }

        private static String payload(OrderEventMessage message, String key) {
            return message.payload() == null ? "" : message.payload().getOrDefault(key, "");
        }
    }
}
