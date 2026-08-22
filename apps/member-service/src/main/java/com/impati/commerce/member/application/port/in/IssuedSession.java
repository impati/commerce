package com.impati.commerce.member.application.port.in;

/** 발급된 세션. 토큰은 이때만 평문으로 존재한다. */
public record IssuedSession(String token, String expiresAt) {
}
