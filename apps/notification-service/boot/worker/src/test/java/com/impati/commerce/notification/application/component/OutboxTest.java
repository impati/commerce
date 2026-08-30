package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.MailDispatchUseCase;
import com.impati.commerce.notification.application.port.out.MailSender;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.DeliveryStatus;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

/**
 * 아웃박스가 발송을 요청 처리에서 떼어내는지, 실패가 사라지지 않는지 확인한다.
 *
 * <p>스케줄러가 배경에서 대기 항목을 집어가면 결과가 흔들리므로 주기를 아주 길게 둔다.
 * 발송은 테스트가 직접 호출한다.
 *
 * <p>멱등 키는 테스트마다 다르게 준다. 같은 값을 쓰면 유니크 제약에 걸려 두 번째 테스트가
 * 자기 알림을 만들지 못한다 — 그것이 바로 이 제약이 하는 일이다 (ADR-0011).
 */
@SpringBootTest(properties = {
        "notifications.dispatch-interval=3600000",
        "notifications.dispatch-retry-delay=60s"
})
@RequiresDatabase
@Import(TestClockConfig.class)
class OutboxTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MailDispatchUseCase mailDispatchUseCase;

    @Autowired
    private MutableClock clock;

    @MockBean
    private MailSender mailSender;

    /** [PD-0009-R1] 기록만으로는 발송되지 않는다. 메일 시스템 장애가 가입 실패가 되지 않게 하는 성질이다. */
    @Test
    void recordsPendingWithoutSending() {
        doNothing().when(mailSender).send(anyString(), anyString(), anyString(), anyString());

        seedMail("pending@impati.dev", "tok_pending", "vmail_pending");

        var entry = outboxOf("pending@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(entry.attempts()).isZero();
        assertThat(entry.body()).contains("tok_pending");
    }

    /** [PD-0009-R1] 기록된 알림이 별도 발송으로 나가는 것을 잡는다. 발송 주기는 보지 않는다. */
    @Test
    void dispatchMarksSent() {
        doNothing().when(mailSender).send(anyString(), anyString(), anyString(), anyString());
        seedMail("sent@impati.dev", "tok_sent", "vmail_sent");

        mailDispatchUseCase.dispatchPending();

        var entry = outboxOf("sent@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(entry.attempts()).isEqualTo(1);
    }

    /**
     * [PD-0009-R4] 발송 실패가 기록으로 남는다. 예외를 삼켜 사라지게 하지 않는다. 3회를 채우면 실패로 확정된다.
     *
     * <p>주기 사이에 시계를 미는 이유는 점유가 다음 시도 시각을 밀어두기 때문이다. 밀지 않으면
     * 두 번째 주기가 같은 건을 집지 못해 시도 횟수가 늘지 않는다 (ADR-0011).
     */
    @Test
    void keepsFailureVisibleAndRetriesUntilLimit() {
        doThrow(new IllegalStateException("smtp down"))
                .when(mailSender).send(anyString(), anyString(), anyString(), anyString());
        seedMail("fail@impati.dev", "tok_fail", "vmail_fail");

        mailDispatchUseCase.dispatchPending();
        assertThat(outboxOf("fail@impati.dev").deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(outboxOf("fail@impati.dev").attempts()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(120));
        mailDispatchUseCase.dispatchPending();
        clock.advance(Duration.ofSeconds(120));
        mailDispatchUseCase.dispatchPending();

        var exhausted = outboxOf("fail@impati.dev");
        assertThat(exhausted.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(exhausted.attempts()).isEqualTo(3);
    }

    private Notification outboxOf(String recipient) {
        return notificationRepository.findAll().stream()
                .filter(entry -> recipient.equals(entry.recipient()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 발송 대상을 저장소로 직접 만든다.
     *
     * <p>예전에는 수신 유스케이스로 넣었지만, 수신과 발송이 다른 실행 단위가 되면서 워커에는
     * 그 유스케이스가 없다 (ADR-0015). 워커의 테스트는 자기 저장소로 준비한다.
     */
    private void seedMail(String recipient, String token, String idempotencyKey) {
        notificationRepository.saveIfAbsent(Notification.mail(
                "EmailVerificationRequested", "mem_test", recipient,
                "이메일 주소를 확인해주세요", "본문 " + token, idempotencyKey));
    }
}
