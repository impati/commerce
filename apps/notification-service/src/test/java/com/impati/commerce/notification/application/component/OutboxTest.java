package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.in.OutboxEntry;
import com.impati.commerce.notification.application.port.out.MailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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
        "spring.datasource.url=jdbc:h2:mem:notification-outbox;DB_CLOSE_DELAY=-1",
        "notifications.dispatch-interval=3600000"
})
class OutboxTest {
    @Autowired
    private NotificationUseCase notificationUseCase;

    @MockBean
    private MailSender mailSender;

    /** [PD-0009-R1] 기록만으로는 발송되지 않는다. 메일 시스템 장애가 가입 실패가 되지 않게 하는 성질이다. */
    @Test
    void recordsPendingWithoutSending() {
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());

        notificationUseCase.requestEmailVerification(
                "mem_pending", "pending@impati.dev", "tok_pending", "vmail_pending");

        var entry = outboxOf("pending@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo("PENDING");
        assertThat(entry.attempts()).isZero();
        assertThat(entry.body()).contains("tok_pending");
    }

    /** [PD-0009-R1] 기록된 알림이 별도 발송으로 나가는 것을 잡는다. 발송 주기는 보지 않는다. */
    @Test
    void dispatchMarksSent() {
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
        notificationUseCase.requestEmailVerification(
                "mem_sent", "sent@impati.dev", "tok_sent", "vmail_sent");

        notificationUseCase.dispatchPending();

        var entry = outboxOf("sent@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo("SENT");
        assertThat(entry.attempts()).isEqualTo(1);
    }

    /**
     * [PD-0009-R4] 발송 실패가 기록으로 남는다. 예외를 삼켜 사라지게 하지 않는다. 3회를 채우면 실패로 확정된다.
     */
    @Test
    void keepsFailureVisibleAndRetriesUntilLimit() {
        doThrow(new IllegalStateException("smtp down"))
                .when(mailSender).send(anyString(), anyString(), anyString());
        notificationUseCase.requestEmailVerification(
                "mem_fail", "fail@impati.dev", "tok_fail", "vmail_fail");

        notificationUseCase.dispatchPending();
        assertThat(outboxOf("fail@impati.dev").deliveryStatus()).isEqualTo("PENDING");
        assertThat(outboxOf("fail@impati.dev").attempts()).isEqualTo(1);

        notificationUseCase.dispatchPending();
        notificationUseCase.dispatchPending();

        var exhausted = outboxOf("fail@impati.dev");
        assertThat(exhausted.deliveryStatus()).isEqualTo("FAILED");
        assertThat(exhausted.attempts()).isEqualTo(3);
    }

    /** [PD-0009-R2] 기록만 남기는 알림은 발송 대상이 아니다. */
    @Test
    void plainRecordIsNotQueuedForDelivery() {
        notificationUseCase.record("OrderPaid", "mem_plain", "Order paid", "Order ord_plain has been paid.");

        var entry = notificationUseCase.outbox().stream()
                .filter(candidate -> "Order paid".equals(candidate.subject()))
                .findFirst()
                .orElseThrow();
        assertThat(entry.channel()).isEqualTo("NONE");
        assertThat(entry.deliveryStatus()).isEqualTo("SKIPPED");
    }

    private OutboxEntry outboxOf(String recipient) {
        return notificationUseCase.outbox().stream()
                .filter(entry -> recipient.equals(entry.recipient()))
                .findFirst()
                .orElseThrow();
    }
}
