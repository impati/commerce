package com.impati.commerce.notification.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.out.MailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 발송 요청을 여러 번 받아도 한 번만 기록하는지 확인한다 (ADR-0011).
 *
 * <p>발신측은 응답을 받지 못하면 재시도할 수밖에 없다. 그 재시도가 사용자에게 메일 두 통이
 * 되지 않게 하는 것이 수신측의 일이고, 여기서 잡는 것이 그 성질이다.
 *
 * <p>스케줄러가 배경에서 대기 항목을 집어가면 결과가 흔들리므로 주기를 아주 길게 둔다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-idempotent;DB_CLOSE_DELAY=-1",
        "notifications.dispatch-interval=3600000"
})
class IdempotentReceiveTest {
    @Autowired
    private NotificationUseCase notificationUseCase;

    @MockBean
    private MailSender mailSender;

    /** 같은 키로 두 번 요청해도 알림은 하나다. 두 번째는 먼저 기록된 것을 그대로 돌려준다. */
    @Test
    void sameKeyRecordsOnce() {
        var first = notificationUseCase.requestEmailVerification(
                "mem_idem", "idem@impati.dev", "tok_first", "vmail_idem");
        var second = notificationUseCase.requestEmailVerification(
                "mem_idem", "idem@impati.dev", "tok_first", "vmail_idem");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(recipientCountOf("idem@impati.dev")).isEqualTo(1);
    }

    /**
     * 두 번째 응답이 저장된 것이어야 한다.
     *
     * <p>중복을 걸렀다고 로그만 남기고 방금 만든 객체를 돌려주면, 발신자는 200과 함께 DB에
     * 없는 식별자를 받는다. 그래서 응답의 id로 실제 조회가 되는지까지 본다.
     */
    @Test
    void duplicateResponseRefersToTheStoredNotification() {
        notificationUseCase.requestEmailVerification(
                "mem_stored", "stored@impati.dev", "tok_stored", "vmail_stored");
        var duplicate = notificationUseCase.requestEmailVerification(
                "mem_stored", "stored@impati.dev", "tok_stored", "vmail_stored");

        assertThat(notificationUseCase.outbox().stream().map(entry -> entry.id()))
                .contains(duplicate.id());
    }

    /** 키가 다르면 다른 요청이다. 사용자가 요청한 재발송이 이 경로로 온다. */
    @Test
    void differentKeysRecordSeparately() {
        notificationUseCase.requestEmailVerification(
                "mem_resend", "resend@impati.dev", "tok_one", "vmail_resend_1");
        notificationUseCase.requestEmailVerification(
                "mem_resend", "resend@impati.dev", "tok_two", "vmail_resend_2");

        assertThat(recipientCountOf("resend@impati.dev")).isEqualTo(2);
    }

    /**
     * 키가 없으면 거절한다.
     *
     * <p>선택값으로 두면 키를 빠뜨린 호출자가 조용히 중복 발송으로 돌아가고, 그 사실이 메일이
     * 두 통 도착할 때까지 드러나지 않는다.
     */
    @Test
    void rejectsMissingKey() {
        assertThatThrownBy(() -> notificationUseCase.requestEmailVerification(
                "mem_nokey", "nokey@impati.dev", "tok_nokey", null))
                .isInstanceOf(DomainException.class);

        assertThatThrownBy(() -> notificationUseCase.requestEmailVerification(
                "mem_blankkey", "blankkey@impati.dev", "tok_blank", "  "))
                .isInstanceOf(DomainException.class);
    }

    private long recipientCountOf(String recipient) {
        return notificationUseCase.outbox().stream()
                .filter(entry -> recipient.equals(entry.recipient()))
                .count();
    }
}
