package com.impati.commerce.member.application.component;

import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.member.application.port.out.VerificationMailRepository;
import com.impati.commerce.member.domain.MemberModels.VerificationMail;
import com.impati.commerce.member.domain.MemberModels.VerificationMailStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 저장이 실패했을 때의 성질을 고정한다 (ADR-0010).
 *
 * <p>저장 실패는 실제 DB로 재현할 수 없어 저장소를 목으로 세운다. 여기서 지키려는 규칙은
 * <b>저장되지 않은 것은 일어나지 않은 것</b>이다 — 기록에 실패하면 항목이 PENDING으로 남아
 * 다음 주기가 처음부터 다시 하고, 그 사이에 잘못된 상태가 굳지 않는다.
 */
class VerificationMailDispatchExecutorTest {
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    /**
     * 천장을 2로 잡는다. 3이면 이 결함을 상태로 구분할 수 없다 — 전이가 두 번 일어나도
     * attempts가 2에 그쳐 FAILED로 넘어가지 않으므로, 깨진 코드와 고친 코드가 같은 상태를
     * 남긴다. 2로 잡으면 두 번째 전이가 곧바로 종단을 밟는다.
     */
    private static final int MAX_ATTEMPTS = 2;

    /**
     * 전송에 성공한 메일은 저장이 실패해도 실패로 뒤집히지 않는다.
     *
     * <p>이 성질이 깨졌던 것이 이전 구조다. 저장을 {@code try} 안에 두면 그 실패가 catch로
     * 흘러 이미 SENT가 된 객체 위에 {@code markFailed}가 겹쳐 쓰이고, 나간 메일이 FAILED로
     * 기록됐다. 지금은 {@code try}가 전송만 감싸므로 그 경로가 존재하지 않는다.
     *
     * <p>상태와 함께 시도 횟수를 단정하는 이유는 그것이 <b>전이가 몇 번 일어났는지</b>를
     * 직접 말하기 때문이다. 한 번의 처리에서 전이는 한 번뿐이어야 하므로 여기서 attempts는
     * 반드시 1이다. 상태만 보면 천장 값에 따라 결함이 가려진다.
     */
    @Test
    void neverMarksFailedWhenSendAlreadySucceeded() {
        var mail = new VerificationMail("mem_1", "user@impati.dev", "raw-token");
        var verificationMailRepository = repositoryReturning(mail);
        var notificationClient = mock(NotificationClient.class);
        doThrow(new IllegalStateException("db is down")).when(verificationMailRepository).save(any());

        var summary = executor(verificationMailRepository, notificationClient).dispatchPending();

        assertThat(mail.status()).isEqualTo(VerificationMailStatus.SENT);
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(mail.token()).isNull();
        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.sent()).isZero();
        verify(notificationClient, times(1))
                .requestEmailVerification("mem_1", "user@impati.dev", "raw-token", mail.id());
    }

    /** 한 건의 저장 실패가 나머지 항목을 막지 않는다. 그 건은 PENDING으로 남아 다음 주기가 가져간다. */
    @Test
    void keepsGoingAfterOneItemFailsToRecord() {
        var broken = new VerificationMail("mem_1", "broken@impati.dev", "raw-broken");
        var healthy = new VerificationMail("mem_2", "healthy@impati.dev", "raw-healthy");
        var verificationMailRepository = mock(VerificationMailRepository.class);
        when(verificationMailRepository.findDispatchCandidates(anyInt()))
                .thenReturn(List.of(broken.id(), healthy.id()));
        when(verificationMailRepository.claimForDispatch(eq(broken.id()), any())).thenReturn(Optional.of(broken));
        when(verificationMailRepository.claimForDispatch(eq(healthy.id()), any())).thenReturn(Optional.of(healthy));
        doThrow(new IllegalStateException("db hiccup")).when(verificationMailRepository).save(broken);

        var summary = executor(verificationMailRepository, mock(NotificationClient.class)).dispatchPending();

        assertThat(summary.claimed()).isEqualTo(2);
        assertThat(summary.sent()).isEqualTo(1);
        assertThat(healthy.status()).isEqualTo(VerificationMailStatus.SENT);
    }

    private VerificationMailRepository repositoryReturning(VerificationMail mail) {
        var verificationMailRepository = mock(VerificationMailRepository.class);
        when(verificationMailRepository.findDispatchCandidates(anyInt())).thenReturn(List.of(mail.id()));
        when(verificationMailRepository.claimForDispatch(eq(mail.id()), any())).thenReturn(Optional.of(mail));
        return verificationMailRepository;
    }

    private VerificationMailDispatchExecutor executor(
            VerificationMailRepository verificationMailRepository,
            NotificationClient notificationClient
    ) {
        return new VerificationMailDispatchExecutor(
                verificationMailRepository, notificationClient, 50, RETRY_DELAY, MAX_ATTEMPTS);
    }
}
