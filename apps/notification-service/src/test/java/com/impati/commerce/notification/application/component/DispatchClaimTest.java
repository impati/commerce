package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.in.OutboxEntry;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발송이 한 번만 일어나는지 확인한다 (ADR-0011).
 *
 * <p>세 가지를 본다 — 점유가 최소 간격 안의 재집기를 막는가, 벤더가 이미 수락했으면 다시
 * 보내지 않는가, 수락 여부를 모를 때 보내지 않는가.
 *
 * <p>스케줄러가 배경에서 대기 항목을 집어가면 결과가 흔들리므로 주기를 아주 길게 둔다.
 */
@SpringBootTest(properties = {
        "notifications.dispatch-interval=3600000",
        "notifications.dispatch-retry-delay=60s"
})
@RequiresDatabase
@Import(TestClockConfig.class)
class DispatchClaimTest {

    @Autowired
    private NotificationUseCase notificationUseCase;

    @Autowired
    private MutableClock clock;

    @MockBean
    private MailSender mailSender;

    /**
     * 점유한 건은 최소 간격이 지나기 전에 다시 집히지 않고, 지나면 다시 집힌다.
     *
     * <p>인스턴스가 여럿일 때 같은 메일이 두 번 나가는 것을 막는 성질이며, 여기서는 같은
     * 인스턴스가 연속으로 주기를 도는 것으로 대신 관찰한다 — 배타성이 스케줄러가 아니라
     * 저장소에 있으므로 호출자가 누구인지는 결과를 바꾸지 않는다.
     *
     * <p>발송을 실패시키는 이유는 성공하면 상태가 SENT가 되어 <b>점유가 아니라 상태 때문에</b>
     * 후보에서 빠지기 때문이다. 그러면 점유를 지워도 테스트가 통과한다.
     */
    @Test
    void keepsClaimedItemOutOfReachUntilRetryDelay() {
        when(mailSender.wasAccepted(anyString())).thenReturn(false);
        doThrow(new IllegalStateException("smtp down"))
                .when(mailSender).send(anyString(), anyString(), anyString(), anyString());
        notificationUseCase.requestEmailVerification(
                "mem_claim", "claim@impati.dev", "tok_claim", "vmail_claim");

        notificationUseCase.dispatchPending();
        assertThat(outboxOf("claim@impati.dev").attempts()).isEqualTo(1);

        notificationUseCase.dispatchPending();
        assertThat(outboxOf("claim@impati.dev").attempts())
                .as("최소 간격 안에는 다시 집히지 않는다")
                .isEqualTo(1);

        clock.advance(Duration.ofSeconds(120));

        notificationUseCase.dispatchPending();
        assertThat(outboxOf("claim@impati.dev").attempts())
                .as("간격이 지나면 다시 집힌다. 점유가 영구 배제가 되면 안 된다")
                .isEqualTo(2);
    }

    /**
     * 벤더가 이미 수락했으면 다시 보내지 않고 종단시킨다.
     *
     * <p>전송에는 성공했는데 결과를 적기 전에 끊긴 경우다. 그 알림은 PENDING으로 남아 있으므로
     * 다음 주기가 집는데, 그때 다시 보내면 사용자에게 두 통이 간다.
     */
    @Test
    void settlesWithoutResendWhenVendorAlreadyAccepted() {
        when(mailSender.wasAccepted(anyString())).thenReturn(true);
        notificationUseCase.requestEmailVerification(
                "mem_accepted", "accepted@impati.dev", "tok_accepted", "vmail_accepted");

        notificationUseCase.dispatchPending();

        verify(mailSender, never()).send(anyString(), anyString(), anyString(), anyString());
        var entry = outboxOf("accepted@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo("SENT");
    }

    /**
     * 수락 여부를 모르면 보내지 않고, 시도 횟수도 쓰지 않는다.
     *
     * <p>모르는 상태에서 보내는 쪽을 고르면 조회 장애가 곧 중복 발송이 된다. 보내보지 않았으므로
     * 실패도 아니다 — 시도 횟수를 깎으면 조회 장애만으로 알림이 FAILED로 확정된다.
     */
    @Test
    void doesNotSendWhenAcceptanceIsUnknown() {
        when(mailSender.wasAccepted(anyString())).thenThrow(new IllegalStateException("vendor unreachable"));
        notificationUseCase.requestEmailVerification(
                "mem_unknown", "unknown@impati.dev", "tok_unknown", "vmail_unknown");

        assertThat(notificationUseCase.dispatchPending()).isZero();

        verify(mailSender, never()).send(anyString(), anyString(), anyString(), anyString());
        var entry = outboxOf("unknown@impati.dev");
        assertThat(entry.deliveryStatus()).isEqualTo("PENDING");
        assertThat(entry.attempts()).isZero();
    }

    private OutboxEntry outboxOf(String recipient) {
        return notificationUseCase.outbox().stream()
                .filter(entry -> recipient.equals(entry.recipient()))
                .findFirst()
                .orElseThrow();
    }
}
