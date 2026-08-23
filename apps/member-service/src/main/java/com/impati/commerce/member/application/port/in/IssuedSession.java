package com.impati.commerce.member.application.port.in;

/**
 * 로그인으로 발급된 것.
 *
 * <p>세션 토큰은 이때만 평문으로 존재한다. 접근 토큰은 그 세션에서 파생된 단명 증명서이며
 * 만료되면 세션 토큰으로 다시 받는다 (ADR-0007).
 */
public record IssuedSession(
        String sessionToken,
        String sessionExpiresAt,
        String accessToken,
        String accessTokenExpiresAt
) {
}
