package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.out.MailSender;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 잘못된 설정으로는 뜨지 않는다 (ADR-0011).
 *
 * <p>세 값 모두 <b>조용히 잘못 도는</b> 실패를 만든다 — 예외도 로그도 없이 인증 메일만 나가지
 * 않거나 두 번 나간다. 오타 하나로 그렇게 되느니 기동에 실패하는 편이 낫다.
 */
class NotificationDispatchConfigTest {
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
        assertThatThrownBy(() -> executor(20, Duration.ZERO, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-delay");
    }

    /** 0이면 첫 시도에서 곧바로 포기한다. 재시도가 목적인데 재시도가 없어진다. */
    @Test
    void rejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> executor(20, VALID_DELAY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-attempts");
    }

    private NotificationExecutor executor(int batchSize, Duration retryDelay, int maxAttempts) {
        return new NotificationExecutor(
                mock(NotificationRepository.class),
                mock(MailSender.class),
                "https://shop.impati.dev/verify",
                batchSize,
                retryDelay,
                maxAttempts
        );
    }
}
