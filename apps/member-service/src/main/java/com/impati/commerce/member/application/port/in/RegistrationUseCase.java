package com.impati.commerce.member.application.port.in;

/** 가입과 이메일 소유 확인 (PD-0001). */
public interface RegistrationUseCase {
    MemberDetails register(String email, String name, String rawPassword);

    void resendVerification(String memberId);

    MemberDetails verifyEmail(String rawToken);
}
