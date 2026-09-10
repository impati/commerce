package com.impati.commerce.order.adapter.out.publisher;

import com.impati.commerce.common.ApiContracts.OrderEventMessage;
import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 주문 사건을 카프카로 발행한다 (ADR-0016).
 *
 * <p><b>브로커가 받았다는 것이 확정된 뒤에 돌아온다.</b> 부르는 쪽은 이 메서드가 정상 반환하면
 * 아웃박스의 그 행을 PUBLISHED로 적고 다시 보내지 않는다. 결과를 기다리지 않고 돌아오면 아웃박스가
 * 거짓말을 하게 되고, ADR-0010이 막으려던 이중 쓰기가 그대로 생긴다 — 브로커는 아웃박스의
 * 대안이 아니라 아웃박스 뒤에 오는 것이다.
 *
 * <p>수락의 강도는 설정이 정한다. {@code acks=all}과 토픽의 {@code min.insync.replicas}가 함께
 * 있어야 유실이 막힌다 — {@code acks=all}은 "ISR 전부"라는 뜻이라 ISR이 하나로 줄면
 * {@code acks=1}과 같아진다.
 *
 * <p><b>파티션 키는 주문 id다.</b> 같은 주문의 사건이 한 파티션에 떨어져 넣은 순서로 나온다.
 * 넣는 순서가 일어난 순서인 것은 부르는 쪽이 한 주문을 통째로 점유해 순서대로 넘기기 때문이다.
 *
 * <p>구독자의 판단은 여기 없다. 사건은 사실만 담고 문구를 만드는 것도 관심 없는 사건을 거르는
 * 것도 소비자의 일이다.
 */
public class KafkaOrderEventPublisher implements OrderEventPublisher {
    private final KafkaTemplate<String, OrderEventMessage> kafkaTemplate;
    private final String topic;
    private final Duration sendTimeout;

    public KafkaOrderEventPublisher(
            KafkaTemplate<String, OrderEventMessage> kafkaTemplate,
            String topic,
            Duration sendTimeout
    ) {
        if (sendTimeout.isNegative() || sendTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "commerce.kafka.send-timeout must be positive but was " + sendTimeout);
        }
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void publish(OrderEvent event) {
        try {
            kafkaTemplate.send(topic, event.partitionKey(), message(event))
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("order event publish interrupted: " + event.id(), interrupted);
        } catch (TimeoutException timeout) {
            // 결과를 모르는 채로 끝났다. 부르는 쪽이 재시도하며, 브로커가 이미 받았다면 중복이
            // 되지만 그것은 소비측 멱등 키가 없앤다. 유실보다 중복이 낫다.
            throw new IllegalStateException("order event publish timed out: " + event.id(), timeout);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("order event publish rejected: " + event.id(), failure.getCause());
        }
    }

    private OrderEventMessage message(OrderEvent event) {
        return new OrderEventMessage(
                event.id(), event.type().name(), event.orderId(), event.memberId(), event.payload());
    }
}
