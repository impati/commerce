package com.impati.commerce.order.adapter.out.publisher;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로듀서 타임아웃이 서로 어긋난 설정으로는 뜨지 않는다 (ADR-0017).
 *
 * <p>이 값들이 서로를 모른 채 흩어져 있던 것이 이 작업의 출발점이다. 여기서 잡는 것은 그 관계가
 * 코드로 강제되는가 — 우리가 프로듀서보다 먼저 포기하는 설정, 카프카 제약을 어기는 설정으로는
 * 기동이 실패한다.
 */
class KafkaProducerTimeoutsTest {

    private static final Duration LINGER = Duration.ofMillis(0);
    private static final Duration REQUEST = Duration.ofSeconds(15);
    private static final Duration DELIVERY = Duration.ofSeconds(15);
    private static final Duration MAX_BLOCK = Duration.ofSeconds(5);
    private static final Duration SEND = Duration.ofSeconds(17);

    /** 출하되는 세트. linger 0 + request 15 ≤ delivery 15 ≤ send 17. */
    @Test
    void acceptsTheShippedDefaults() {
        assertThatCode(() -> new KafkaProducerTimeouts(LINGER, REQUEST, DELIVERY, MAX_BLOCK, SEND))
                .doesNotThrowAnyException();
    }

    /**
     * send-timeout이 delivery-timeout보다 작으면 뜨지 않는다.
     *
     * <p>우리가 프로듀서보다 먼저 손을 떼는 설정이다 — 이 작업이 없애려는 오판이 정확히 여기서
     * 생긴다. delivery 20초, send 17초.
     */
    @Test
    void rejectsSendTimeoutBelowDeliveryTimeout() {
        assertThatThrownBy(() -> new KafkaProducerTimeouts(
                LINGER, REQUEST, Duration.ofSeconds(20), MAX_BLOCK, Duration.ofSeconds(17)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("send-timeout");
    }

    /**
     * delivery-timeout이 linger + request-timeout을 못 덮으면 뜨지 않는다.
     *
     * <p>카프카 프로듀서 자신의 제약이다. 어기면 프로듀서 생성이 실패하지만, 우리가 먼저 잡아
     * 어느 값이 어긋났는지 우리 언어로 알린다. request 15초, delivery 10초.
     */
    @Test
    void rejectsDeliveryTimeoutBelowLingerPlusRequest() {
        assertThatThrownBy(() -> new KafkaProducerTimeouts(
                LINGER, Duration.ofSeconds(15), Duration.ofSeconds(10), MAX_BLOCK, SEND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delivery-timeout");
    }

    @Test
    void rejectsNonPositiveRequestTimeout() {
        assertThatThrownBy(() -> new KafkaProducerTimeouts(LINGER, Duration.ZERO, DELIVERY, MAX_BLOCK, SEND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request-timeout");
    }

    @Test
    void rejectsNonPositiveDeliveryTimeout() {
        assertThatThrownBy(() -> new KafkaProducerTimeouts(LINGER, REQUEST, Duration.ZERO, MAX_BLOCK, SEND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delivery-timeout");
    }

    @Test
    void rejectsNonPositiveMaxBlock() {
        assertThatThrownBy(() -> new KafkaProducerTimeouts(LINGER, REQUEST, DELIVERY, Duration.ZERO, SEND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-block");
    }

    @Test
    void rejectsNonPositiveSendTimeout() {
        assertThatThrownBy(() -> new KafkaProducerTimeouts(LINGER, REQUEST, DELIVERY, MAX_BLOCK, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("send-timeout");
    }

    @Test
    void rejectsNegativeLinger() {
        assertThatThrownBy(() -> new KafkaProducerTimeouts(
                Duration.ofMillis(-1), REQUEST, DELIVERY, MAX_BLOCK, SEND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linger");
    }

    /** 검증을 통과한 값이 그대로 프로듀서 설정으로 나간다 — 흩어진 두 벌이 되지 않는다. */
    @Test
    void carriesTheValuesIntoTheProducerConfig() {
        var timeouts = new KafkaProducerTimeouts(LINGER, REQUEST, DELIVERY, MAX_BLOCK, SEND);

        assertThat(timeouts.producerConfig())
                .containsEntry(ProducerConfig.LINGER_MS_CONFIG, 0)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15_000)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15_000)
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
    }
}
