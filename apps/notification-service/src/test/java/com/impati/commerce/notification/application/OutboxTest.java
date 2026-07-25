package com.impati.commerce.notification.application;

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
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-outbox;DB_CLOSE_DELAY=-1",
        "notifications.dispatch-interval=3600000"
})
class OutboxTest {
    @Autowired
    private NotificationService notifications;

    @MockBean
    private MailSender mailSender;

    /** 기록만으로는 발송되지 않는다. 메일 시스템 장애가 가입 실패가 되지 않게 하는 성질이다. */
    @Test
    void recordsPendingWithoutSending() {
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());

        notifications.requestEmailVerification("mem_pending", "pending@impati.dev", "tok_pending");

        var entry = outboxOf("pending@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo("PENDING");
        assertThat(entry.attempts()).isZero();
        assertThat(entry.body()).contains("tok_pending");
    }

    @Test
    void dispatchMarksSent() {
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
        notifications.requestEmailVerification("mem_sent", "sent@impati.dev", "tok_sent");

        notifications.dispatchPending();

        var entry = outboxOf("sent@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo("SENT");
        assertThat(entry.attempts()).isEqualTo(1);
    }

    /** 발송 실패가 기록으로 남는다. 예외를 삼켜 사라지게 하지 않는다. */
    @Test
    void keepsFailureVisibleAndRetriesUntilLimit() {
        doThrow(new IllegalStateException("smtp down"))
                .when(mailSender).send(anyString(), anyString(), anyString());
        notifications.requestEmailVerification("mem_fail", "fail@impati.dev", "tok_fail");

        notifications.dispatchPending();
        assertThat(outboxOf("fail@impati.dev").deliveryStatus()).isEqualTo("PENDING");
        assertThat(outboxOf("fail@impati.dev").attempts()).isEqualTo(1);

        notifications.dispatchPending();
        notifications.dispatchPending();

        var exhausted = outboxOf("fail@impati.dev");
        assertThat(exhausted.deliveryStatus()).isEqualTo("FAILED");
        assertThat(exhausted.attempts()).isEqualTo(3);
    }

    /** 기록만 남기는 알림은 발송 대상이 아니다. */
    @Test
    void plainRecordIsNotQueuedForDelivery() {
        notifications.record("OrderPaid", "mem_plain", "Order paid", "Order ord_plain has been paid.");

        var entry = notifications.outbox().stream()
                .filter(candidate -> "Order paid".equals(candidate.subject()))
                .findFirst()
                .orElseThrow();
        assertThat(entry.channel()).isEqualTo("NONE");
        assertThat(entry.deliveryStatus()).isEqualTo("SKIPPED");
    }

    private com.impati.commerce.common.ApiContracts.OutboxEntryResponse outboxOf(String recipient) {
        return notifications.outbox().stream()
                .filter(entry -> recipient.equals(entry.recipient()))
                .findFirst()
                .orElseThrow();
    }
}
