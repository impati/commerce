package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.MailDispatchUseCase;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.out.MailSender;
import com.impati.commerce.notification.support.MutableClock;
import com.impati.commerce.notification.support.TestClockConfig;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 한 주기가 집는 건수에 상한이 있는지 확인한다 (ADR-0011).
 *
 * <p>상한이 없으면 밀린 건수가 한 주기의 길이를 정한다. 그 건수가 커지는 시점이 정확히 메일
 * 시스템 장애 중이고, 그때 한 주기가 몇십 분이 된다.
 *
 * <p>점유가 한 문장이라 상한이 하위 질의의 {@code limit}에만 걸려 있다. 그 한 줄이 빠져도
 * 다른 테스트는 전부 통과하므로 여기서 따로 잡는다.
 */
@SpringBootTest(properties = {
        "notifications.dispatch-interval=3600000",
        "notifications.dispatch-batch-size=2",
        "notifications.dispatch-retry-delay=60s"
})
@RequiresDatabase
@Import(TestClockConfig.class)
class DispatchBatchLimitTest {

    @Autowired
    private NotificationUseCase notificationUseCase;

    @Autowired
    private MailDispatchUseCase mailDispatchUseCase;

    @Autowired
    private MutableClock clock;

    @MockBean
    private MailSender mailSender;

    @Test
    void claimsAtMostTheConfiguredBatchSize() {
        when(mailSender.wasAccepted(anyString())).thenReturn(false);
        for (var index = 0; index < 5; index++) {
            notificationUseCase.requestEmailVerification(
                    "mem_batch", "batch" + index + "@impati.dev", "tok_batch", "vmail_batch_" + index);
        }

        assertThat(mailDispatchUseCase.dispatchPending())
                .as("상한이 2이면 5건이 밀려 있어도 한 주기에 2건만 나간다")
                .isEqualTo(2);

        clock.advance(Duration.ofSeconds(120));
        assertThat(mailDispatchUseCase.dispatchPending()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(120));
        assertThat(mailDispatchUseCase.dispatchPending()).isEqualTo(1);
    }
}
