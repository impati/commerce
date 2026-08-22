package com.impati.commerce.member.application.port.out;

import com.impati.commerce.member.domain.MemberModels.Session;

import java.util.Optional;

/** 세션 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다. */
public interface SessionRepository {
    void save(Session session);

    Optional<Session> findByTokenHash(String tokenHash);
}
