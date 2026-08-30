package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.out.MailSender;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 한 건의 실패가 나머지 배치를 막지 않는지 확인한다 (PD-0016-R5).
 *
 * <p>발송 자체의 실패는 {@code deliverOnce} 안에서 상태로 흡수되므로 다음 건을 막지 않는다.
 * 막을 수 있는 것은 <b>결과를 적는 것까지 실패한</b> 경우다 — 그 예외는 흡수할 자리가 없어
 * 루프를 뚫고 나간다.
 *
 * <p>저장소를 실패시켜야 하므로 스프링 컨텍스트 없이 목으로 세운다.
 */
class DispatchFailureIsolationTest {
    @Test
    void storageFailureOnOneItemDoesNotStopTheRest() {
        var notificationRepository = mock(NotificationRepository.class);
        var mailSender = mock(MailSender.class);
        var failing = mail("vmail_failing", "failing@impati.dev");
        var following = mail("vmail_following", "following@impati.dev");

        when(notificationRepository.claimForDispatch(anyString(), anyInt(), any()))
                .thenReturn(List.of(failing, following));
        when(mailSender.wasAccepted(anyString())).thenReturn(false);
        doThrow(new IllegalStateException("db down")).when(notificationRepository).save(failing);

        var settled = executor(notificationRepository, mailSender).dispatchPending();

        verify(mailSender).send(eq("following@impati.dev"), anyString(), anyString(), anyString());
        assertThat(settled)
                .as("앞 건의 저장이 실패해도 뒤 건은 처리된다")
                .isEqualTo(1);
    }

    private Notification mail(String idempotencyKey, String recipient) {
        return Notification.mail(
                "EmailVerificationRequested", "mem_isolation", recipient,
                "subject", "body", idempotencyKey);
    }

    private MailDispatchExecutor executor(NotificationRepository repository, MailSender mailSender) {
        return new MailDispatchExecutor(repository, mailSender, 5, Duration.ofSeconds(60), 3);
    }
}
