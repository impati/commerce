package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 잘못된 설정으로는 뜨지 않는다 (ADR-0012, ADR-0017).
 *
 * <p>값 모두 <b>조용히 잘못 도는</b> 실패를 만든다 — 예외도 로그도 없이 사건이 나가지 않거나
 * 두 번 나간다. 오타 하나로 그렇게 되느니 기동에 실패하는 편이 낫다.
 */
class OrderEventPublishConfigTest {
    /** 출하되는 값. 주문 3 × 사건 7 × (max-block 5초 + send 17초) = 462초를 덮는다. */
    private static final Duration VALID_DELAY = Duration.ofSeconds(480);

    /** 발행 타임아웃의 출하 기본값. */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(17);

    /** send()가 버퍼 참·메타데이터 없음에 블로킹하는 상한의 출하 기본값. */
    private static final Duration MAX_BLOCK = Duration.ofSeconds(5);

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
     */
    @Test
    void rejectsLeaseShorterThanTheBatch() {
        assertThatThrownBy(() -> executor(20, VALID_DELAY, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover the whole batch");
    }

    /** 기본값은 이 관계를 만족한다. 주문 3 × 사건 7 × 22초 = 462초 ≤ 480초. */
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
        assertThatThrownBy(() -> executor(3, VALID_DELAY, 5, Duration.ofSeconds(30), MAX_BLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover the whole batch");
    }

    /**
     * max-block도 건당 최악에 포함된다.
     *
     * <p>{@code send()}가 버퍼 참·메타데이터 없음에 블로킹하는 시간을 빼면, 버퍼가 차는 바로 그
     * 순간 임차가 배치를 못 덮어 순서 보장이 무너진다. send-timeout이 작아도 max-block이 크면
     * 검사가 걸려야 한다 (ADR-0017).
     */
    @Test
    void maxBlockIsCountedInTheWorstCase() {
        assertThatThrownBy(() -> executor(3, VALID_DELAY, 5, Duration.ofSeconds(1), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover the whole batch");
    }

    /** 0이면 발행이 기다리지 않는 셈이고, 임차 계산도 무력해진다. */
    @Test
    void rejectsNonPositiveSendTimeout() {
        assertThatThrownBy(() -> executor(3, VALID_DELAY, 5, Duration.ZERO, MAX_BLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("send-timeout");
    }

    /** 0이면 send()의 블로킹 항이 사라져 임차 계산이 실제 스레드 점유를 과소평가한다. */
    @Test
    void rejectsNonPositiveMaxBlock() {
        assertThatThrownBy(() -> executor(3, VALID_DELAY, 5, SEND_TIMEOUT, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-block");
    }

    private OrderEventPublishExecutor executor(int orderBatchSize, Duration retryDelay, int maxAttempts) {
        return executor(orderBatchSize, retryDelay, maxAttempts, SEND_TIMEOUT, MAX_BLOCK);
    }

    private OrderEventPublishExecutor executor(
            int orderBatchSize, Duration retryDelay, int maxAttempts, Duration sendTimeout, Duration maxBlock) {
        return new OrderEventPublishExecutor(
                mock(OrderEventRepository.class), mock(OrderEventPublisher.class),
                orderBatchSize, retryDelay, maxAttempts, sendTimeout, maxBlock);
    }
}
