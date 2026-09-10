package com.impati.commerce.order.adapter.out.publisher;

import org.apache.kafka.clients.producer.ProducerConfig;

import java.time.Duration;
import java.util.Map;

/**
 * 프로듀서의 시간 노브를 한 세트로 들고 관계를 기동 시 검증한다 (ADR-0017).
 *
 * <p>이 값들이 서로를 모른 채 흩어져 있던 것이 문제였다. 발행 어댑터는 {@code send-timeout}에
 * 손을 떼는데 프로듀서의 {@code delivery.timeout.ms}는 설정되지 않아 기본 120초가 적용됐고,
 * 우리가 먼저 포기해 성공한 사건을 실패로 적었다.
 *
 * <p><b>프로듀서가 먼저 결판나게 한다.</b> {@code delivery.timeout <= send-timeout}이면 Future가
 * 우리가 손을 떼기 전에 성공이나 확정 실패로 끝나므로, 우리가 적는 판정이 프로듀서의 판정과
 * 같아진다. {@code send-timeout}은 이제 결과 판정원이 아니라 프로듀서가 자기 시한을 어길 때
 * 릴레이 스레드가 무한히 붙잡히지 않게 하는 상한이다.
 *
 * <p>{@code linger + request.timeout <= delivery.timeout}은 카프카 자신의 제약이다. 어기면
 * 프로듀서 생성이 실패하지만, 우리가 먼저 잡아 어느 값이 어긋났는지 우리 언어로 알린다.
 */
public final class KafkaProducerTimeouts {
    private final Duration linger;
    private final Duration requestTimeout;
    private final Duration deliveryTimeout;
    private final Duration maxBlock;
    private final Duration sendTimeout;

    public KafkaProducerTimeouts(
            Duration linger,
            Duration requestTimeout,
            Duration deliveryTimeout,
            Duration maxBlock,
            Duration sendTimeout) {
        requireNonNegative("commerce.kafka.linger", linger);
        requirePositive("commerce.kafka.request-timeout", requestTimeout);
        requirePositive("commerce.kafka.delivery-timeout", deliveryTimeout);
        requirePositive("commerce.kafka.max-block", maxBlock);
        requirePositive("commerce.kafka.send-timeout", sendTimeout);
        if (linger.plus(requestTimeout).compareTo(deliveryTimeout) > 0) {
            throw new IllegalArgumentException(
                    "commerce.kafka.delivery-timeout must cover linger + request-timeout ("
                            + linger + " + " + requestTimeout + ") but was " + deliveryTimeout);
        }
        if (deliveryTimeout.compareTo(sendTimeout) > 0) {
            throw new IllegalArgumentException(
                    "commerce.kafka.send-timeout must be at least delivery-timeout so the producer "
                            + "settles first: delivery-timeout " + deliveryTimeout + " but send-timeout "
                            + sendTimeout);
        }
        this.linger = linger;
        this.requestTimeout = requestTimeout;
        this.deliveryTimeout = deliveryTimeout;
        this.maxBlock = maxBlock;
        this.sendTimeout = sendTimeout;
    }

    /** 발행 어댑터가 결과를 기다리는 상한. */
    public Duration sendTimeout() {
        return sendTimeout;
    }

    /** {@code send()}가 버퍼 참·메타데이터 없음에 블로킹하는 상한. 임차 계산이 이것을 포함한다. */
    public Duration maxBlock() {
        return maxBlock;
    }

    /** 자동 구성된 프로듀서에 실제로 실을 값. 검증을 통과한 세트만 여기 온다. */
    public Map<String, Object> producerConfig() {
        return Map.of(
                ProducerConfig.LINGER_MS_CONFIG, (int) linger.toMillis(),
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) requestTimeout.toMillis(),
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) deliveryTimeout.toMillis(),
                ProducerConfig.MAX_BLOCK_MS_CONFIG, (int) maxBlock.toMillis());
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive but was " + value);
        }
    }

    private static void requireNonNegative(String name, Duration value) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative but was " + value);
        }
    }
}
