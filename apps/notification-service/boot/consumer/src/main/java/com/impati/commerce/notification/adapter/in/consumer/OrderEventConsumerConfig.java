package com.impati.commerce.notification.adapter.in.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

/**
 * 소비 실패를 어떻게 다룰지 정한다 (ADR-0016).
 *
 * <p><b>기본값이 조용한 유실 쪽이라 명시한다.</b> 스프링 카프카의 기본 오류 처리기는 몇 번
 * 재시도한 뒤 <b>로그만 남기고 오프셋을 올린다.</b> 그러면 처리하지 못한 사건이 사라지고, 그
 * 사실은 로그 한 줄로만 남는다. 아무것도 하지 않으면 그 동작을 고른 것이 된다.
 *
 * <p>대신 <b>성공할 때까지 막는다.</b> 재시도 횟수에 상한을 두지 않으므로 그 파티션이 멈추고
 * 밀린 건수가 알람이 된다. 건너뛰는 것은 소리 없는 손상이고, DLQ로 흘려보내는 것은 그 주문의
 * 순서를 거기서 깨뜨린다 — 릴레이가 주문 단위로 점유해가며 지킨 것이 무의미해진다.
 *
 * <p>막히는 원인이 일시적이면(DB 장애) 스스로 풀리고, 영구적이면(직렬화 실패, 코드 결함) 사람이
 * 봐야 한다. 후자를 조용히 넘기지 않는 것이 요점이다.
 */
@Configuration
public class OrderEventConsumerConfig {

    /**
     * 재시도 간격.
     *
     * <p>0으로 두면 실패한 레코드를 쉼 없이 다시 처리해 브로커와 DB를 두들긴다. 막히는 것 자체는
     * 의도이지만 그동안 다른 것까지 느려지게 할 이유는 없다.
     */
    private final Duration retryInterval;

    public OrderEventConsumerConfig(
            @Value("${commerce.kafka.consume-retry-interval:5s}") Duration retryInterval) {
        if (retryInterval.isNegative() || retryInterval.isZero()) {
            throw new IllegalArgumentException(
                    "commerce.kafka.consume-retry-interval must be positive but was " + retryInterval);
        }
        this.retryInterval = retryInterval;
    }

    @Bean
    CommonErrorHandler orderEventErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(retryInterval.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
