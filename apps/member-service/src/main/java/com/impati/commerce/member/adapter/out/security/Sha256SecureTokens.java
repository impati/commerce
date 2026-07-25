package com.impati.commerce.member.adapter.out.security;

import com.impati.commerce.member.application.SecureTokens;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 256비트 난수 토큰과 SHA-256 해시.
 *
 * <p>비밀번호와 달리 토큰은 엔트로피가 충분하므로 느린 해시(BCrypt)가 필요하지 않다. 오히려
 * 결정적 해시여야 조회가 가능하다. 사전 공격 대상이 아니라 솔트도 필요하지 않다.
 */
@Component
public class Sha256SecureTokens implements SecureTokens {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String newToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
