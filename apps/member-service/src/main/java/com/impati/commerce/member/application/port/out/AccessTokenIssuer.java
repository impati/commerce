package com.impati.commerce.member.application.port.out;

/**
 * 세션을 근거로 단명 접근 토큰을 발급한다.
 *
 * <p>게이트웨이는 이 토큰을 저장소 조회 없이 스스로 검증한다. 그래서 member-service가 죽어도
 * 토큰이 살아 있는 동안은 인증이 동작한다. 근거는 ADR-0007.
 *
 * <p>수명이 곧 폐기 지연 상한이다 — 세션을 폐기해도 이미 발급된 토큰은 만료까지 통한다
 * (PD-0014-R5, PD-0014-R8).
 */
public interface AccessTokenIssuer {
    AccessToken issue(String memberId);
}
