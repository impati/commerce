package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 잘못된 설정으로는 뜨지 않는다 (ADR-0012).
 *
 * <p>네 값 모두 <b>조용히 잘못 도는</b> 실패를 만든다 — 예외도 로그도 없이 사건이 나가지 않거나
 * 두 번 나간다. 오타 하나로 그렇게 되느니 기동에 실패하는 편이 낫다.
 */
class OrderEventPublishConfigTest {
    private static final Duration VALID_DELAY = Duration.ofSeconds(60);

    /** 0이면 점유가 아무것도 집지 못한다. */
    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> executor(0, VALID_DELAY, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");
    }

    /** 0이면 점유가 즉시 만료돼 백오프와 배타성이 함께 사라진다. */
    @Test
    void rejectsNonPositiveRetryDelay() {
        assertThatThrownBy(() -> executor(10, Duration.ZERO, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-delay");
    }

    /** 0이면 첫 시도에서 곧바로 포기한다. 재시도가 목적인데 재시도가 없어진다. */
    @Test
    void rejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> executor(10, VALID_DELAY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-attempts");
    }

    /**
     * 임차가 배치 전체를 덮지 못하면 뜨지 않는다.
     *
     * <p>한 번에 집은 건을 다 처리하기 전에 임차가 만료되면 아직 처리 중인 건을 다른 인스턴스가
     * 집는다. 묶음 점유의 전제가 무너지므로 설정 두 값의 조합으로 깨뜨릴 수 없게 막는다.
     *
     * <p>건당 최악 4초 — 발행 한 번의 외부 호출이고 common-http 기본 타임아웃(연결 1초 ·
     * 읽기 3초)에 묶여 있다.
     */
    @Test
    void rejectsLeaseShorterThanTheBatch() {
        assertThatThrownBy(() -> executor(20, VALID_DELAY, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover the whole batch");
    }

    /** 기본값은 이 관계를 만족한다. 배치 10 × 4초 = 40초 ≤ 60초. */
    @Test
    void acceptsTheShippedDefaults() {
        assertThatCode(() -> executor(10, VALID_DELAY, 5)).doesNotThrowAnyException();
    }

    private OrderEventPublishExecutor executor(int batchSize, Duration retryDelay, int maxAttempts) {
        return new OrderEventPublishExecutor(
                mock(OrderEventRepository.class), mock(OrderEventPublisher.class),
                batchSize, retryDelay, maxAttempts);
    }
}
