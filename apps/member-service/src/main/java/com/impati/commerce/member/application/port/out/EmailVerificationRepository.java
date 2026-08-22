package com.impati.commerce.member.application.port.out;

import com.impati.commerce.member.domain.MemberModels.EmailVerification;

import java.util.Optional;

/** 이메일 인증 토큰 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다. */
public interface EmailVerificationRepository {
    void save(EmailVerification verification);

    Optional<EmailVerification> findByTokenHash(String tokenHash);
}
