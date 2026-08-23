package com.impati.commerce.member.application.port.out;

import java.time.Instant;

/** 발급된 접근 토큰. 서명되어 있으므로 담긴 값은 누구나 읽을 수 있다. */
public record AccessToken(String value, Instant expiresAt) {
}
