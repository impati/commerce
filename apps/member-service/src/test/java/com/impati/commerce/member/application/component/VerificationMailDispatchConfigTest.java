package com.impati.commerce.member.application.component;

import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.member.application.port.out.VerificationMailRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 잘못된 설정으로는 뜨지 않는다 (ADR-0010).
 *
 * <p>세 값 모두 <b>조용히 잘못 도는</b> 실패를 만든다 — 예외도 로그도 없이 인증 메일만 나가지
 * 않는다. 그 결과는 가입한 사람이 로그인하지 못하는 것이므로, 오타 하나로 그렇게 되느니
 * 기동에 실패하는 편이 낫다.
 */
class VerificationMailDispatchConfigTest {
    private static final Duration VALID_DELAY = Duration.ofSeconds(60);

    /** 0이면 후보가 항상 비어 아무것도 집지 않는다. */
    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> executor(0, VALID_DELAY, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");
    }

    /** 0이면 점유가 즉시 만료돼 백오프가 사라진다. 장애 중 재시도 증폭이 되살아난다. */
    @Test
    void rejectsNonPositiveRetryDelay() {
        assertThatThrownBy(() -> executor(50, Duration.ZERO, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-delay");
    }

    /** 0이면 첫 시도에서 곧바로 포기한다. 재시도가 목적인데 재시도가 없어진다. */
    @Test
    void rejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> executor(50, VALID_DELAY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-attempts");
    }

    private VerificationMailDispatchExecutor executor(int batchSize, Duration retryDelay, int maxAttempts) {
        return new VerificationMailDispatchExecutor(
                mock(VerificationMailRepository.class),
                mock(NotificationClient.class),
                batchSize,
                retryDelay,
                maxAttempts
        );
    }
}
