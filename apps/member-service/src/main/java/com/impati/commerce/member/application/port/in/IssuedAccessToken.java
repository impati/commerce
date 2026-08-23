package com.impati.commerce.member.application.port.in;

/** 갱신으로 발급된 접근 토큰. */
public record IssuedAccessToken(String accessToken, String accessTokenExpiresAt) {
}
