package com.impati.commerce.member.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.domain.MemberModels.VerificationMail;
import com.impati.commerce.member.domain.MemberModels.VerificationMailStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 아웃박스 항목의 상태 전이를 고정한다 (ADR-0010).
 *
 * <p>여기서 검증하는 것은 "언제 다시 보낼 것인가"가 아니다. 그것은 저장소의 점유가 정하며
 * 이 객체는 알지 못한다.
 */
class VerificationMailTest {
    private static final int MAX_ATTEMPTS = 3;

    @Test
    void startsPendingWithTokenToSend() {
        var mail = new VerificationMail("mem_1", "user@impati.dev", "raw-token");

        assertThat(mail.status()).isEqualTo(VerificationMailStatus.PENDING);
        assertThat(mail.isPending()).isTrue();
        assertThat(mail.attempts()).isZero();
        assertThat(mail.token()).isEqualTo("raw-token");
    }

    @Test
    void rejectsMailWithoutRecipient() {
        assertThatThrownBy(() -> new VerificationMail("mem_1", " ", "raw-token"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("recipient");
    }

    /**
     * 발송에 성공하면 토큰을 버린다. 평문 토큰을 들고 있던 유일한 이유가 사라졌기 때문이다 —
     * 남겨두면 DB 유출 시 계정 인증 수단이 그대로 나간다.
     */
    @Test
    void discardsTokenOnceSent() {
        var mail = new VerificationMail("mem_1", "user@impati.dev", "raw-token");

        mail.markSent();

        assertThat(mail.status()).isEqualTo(VerificationMailStatus.SENT);
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(mail.token()).isNull();
    }

    /** 한도 전의 실패는 PENDING으로 남아 다음 주기에 다시 집힌다. 보낼 토큰도 그대로 있어야 한다. */
    @Test
    void staysPendingWhileAttemptsRemain() {
        var mail = new VerificationMail("mem_1", "user@impati.dev", "raw-token");

        mail.markFailed("connection refused", MAX_ATTEMPTS);

        assertThat(mail.status()).isEqualTo(VerificationMailStatus.PENDING);
        assertThat(mail.attempts()).isEqualTo(1);
        assertThat(mail.lastError()).isEqualTo("connection refused");
        assertThat(mail.token()).isEqualTo("raw-token");
    }

    /**
     * 한도에 도달하면 포기하고 토큰을 버린다.
     *
     * <p>PENDING과 FAILED를 나누는 이유가 여기서 드러난다. 합쳐두면 포기한 건이 재시도 대기에
     * 섞여 "아직 못 보냈다"와 구분되지 않고, 쌓인 것을 세어볼 수 없다.
     */
    @Test
    void givesUpAtAttemptLimit() {
        var mail = new VerificationMail("mem_1", "user@impati.dev", "raw-token");

        mail.markFailed("timeout", MAX_ATTEMPTS);
        mail.markFailed("timeout", MAX_ATTEMPTS);
        assertThat(mail.status()).isEqualTo(VerificationMailStatus.PENDING);

        mail.markFailed("timeout", MAX_ATTEMPTS);

        assertThat(mail.status()).isEqualTo(VerificationMailStatus.FAILED);
        assertThat(mail.attempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(mail.lastError()).isEqualTo("timeout");
        assertThat(mail.token()).isNull();
    }

    /** 실패 뒤 성공하면 마지막 오류가 남아 있지 않아야 한다. 성공한 건이 실패로 보이면 안 된다. */
    @Test
    void clearsLastErrorWhenLaterAttemptSucceeds() {
        var mail = new VerificationMail("mem_1", "user@impati.dev", "raw-token");
        mail.markFailed("connection refused", MAX_ATTEMPTS);

        mail.markSent();

        assertThat(mail.status()).isEqualTo(VerificationMailStatus.SENT);
        assertThat(mail.attempts()).isEqualTo(2);
        assertThat(mail.lastError()).isNull();
    }
}
