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
    /** 출하되는 값. 주문 3 × 사건 5 × 4초 = 60초를 덮는다. */
    private static final Duration VALID_DELAY = Duration.ofSeconds(90);

    /** 발행 타임아웃의 출하 기본값. 건당 최악 시간이 이 값이다. */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(4);

    /** 0이면 점유가 아무것도 집지 못한다. */
    @Test
    void rejectsNonPositiveOrdersPerCycle() {
        assertThatThrownBy(() -> executor(0, VALID_DELAY, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orders-per-cycle");
    }

    /** 0이면 점유가 즉시 만료돼 백오프와 배타성이 함께 사라진다. */
    @Test
    void rejectsNonPositiveRetryDelay() {
        assertThatThrownBy(() -> executor(3, Duration.ZERO, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-delay");
    }

    /** 0이면 첫 시도에서 곧바로 포기한다. 재시도가 목적인데 재시도가 없어진다. */
    @Test
    void rejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> executor(3, VALID_DELAY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-attempts");
    }

    /**
     * 임차가 배치 전체를 덮지 못하면 뜨지 않는다.
     *
     * <p>한 번에 집은 건을 다 처리하기 전에 임차가 만료되면 아직 처리 중인 주문을 다른
     * 인스턴스가 집는다. 한 주문을 둘이 갖게 되므로 순서 보장이 거기서 무너진다 (ADR-0016).
     *
     * <p>건당 최악 4초 — 발행 한 번의 외부 호출이고 common-http 기본 타임아웃(연결 1초 ·
     * 읽기 3초)에 묶여 있다. 한 주문이 갖는 사건은 상태 전이 종류만큼이다.
     */
    @Test
    void rejectsLeaseShorterThanTheBatch() {
        assertThatThrownBy(() -> executor(20, VALID_DELAY, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover the whole batch");
    }

    /** 기본값은 이 관계를 만족한다. 주문 3 × 사건 5 × 4초 = 60초 ≤ 90초. */
    @Test
    void acceptsTheShippedDefaults() {
        assertThatCode(() -> executor(3, VALID_DELAY, 5)).doesNotThrowAnyException();
    }

    /**
     * 발행 타임아웃을 올리면 임차 검사가 함께 엄해진다.
     *
     * <p>건당 최악 시간을 상수로 두면 이 둘이 어긋난다 — 타임아웃만 올라가고 검사는 옛 값을
     * 쓰므로, 임차가 배치를 못 덮는 설정으로 기동이 성공한다.
     */
    @Test
    void aLongerSendTimeoutTightensTheLeaseCheck() {
        assertThatThrownBy(() -> executor(3, VALID_DELAY, 5, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover the whole batch");
    }

    /** 0이면 발행이 기다리지 않는 셈이고, 임차 계산도 0이 되어 검사가 무력해진다. */
    @Test
    void rejectsNonPositiveSendTimeout() {
        assertThatThrownBy(() -> executor(3, VALID_DELAY, 5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("send-timeout");
    }

    private OrderEventPublishExecutor executor(int orderBatchSize, Duration retryDelay, int maxAttempts) {
        return executor(orderBatchSize, retryDelay, maxAttempts, SEND_TIMEOUT);
    }

    private OrderEventPublishExecutor executor(
            int orderBatchSize, Duration retryDelay, int maxAttempts, Duration sendTimeout) {
        return new OrderEventPublishExecutor(
                mock(OrderEventRepository.class), mock(OrderEventPublisher.class),
                orderBatchSize, retryDelay, maxAttempts, sendTimeout);
    }
}
