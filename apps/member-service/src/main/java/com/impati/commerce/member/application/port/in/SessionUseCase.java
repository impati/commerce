package com.impati.commerce.member.application.port.in;

/** 로그인 세션 (PD-0014). */
public interface SessionUseCase {
    IssuedSession login(String email, String rawPassword);

    SessionOwner resolveSession(String rawToken);

    void logout(String rawToken);
}
