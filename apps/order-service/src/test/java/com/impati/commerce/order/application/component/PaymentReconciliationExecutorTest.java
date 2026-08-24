package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 잘못된 설정값으로는 기동하지 않는다.
 *
 * <p>둘 다 조용히 잘못 도는 실패다. batchSize가 0이면 후보가 항상 비어 정리가 멈추는데 예외도
 * 로그도 없고, retryDelay가 0이면 백오프가 사라져 장애 중인 결제 서비스에 재시도가 몰린다.
 * 대금이 나간 주문을 정리하는 경로이므로 조용히 꺼지는 것보다 뜨지 않는 편이 낫다.
 */
class PaymentReconciliationExecutorTest {
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentClient paymentClient = mock(PaymentClient.class);

    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> executor(0, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");
        assertThatThrownBy(() -> executor(-1, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveRetryDelay() {
        assertThatThrownBy(() -> executor(50, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-delay");
        assertThatThrownBy(() -> executor(50, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> executor(50, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsPositiveSettings() {
        assertThatCode(() -> executor(50, Duration.ofSeconds(60))).doesNotThrowAnyException();
    }

    private PaymentReconciliationExecutor executor(int batchSize, Duration retryDelay) {
        return new PaymentReconciliationExecutor(orderRepository, paymentClient, batchSize, retryDelay);
    }
}
