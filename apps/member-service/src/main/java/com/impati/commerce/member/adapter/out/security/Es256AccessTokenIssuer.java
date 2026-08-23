package com.impati.commerce.member.adapter.out.security;

import com.impati.commerce.member.application.port.out.AccessToken;
import com.impati.commerce.member.application.port.out.AccessTokenIssuer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

/**
 * EC P-256 개인키로 접근 토큰에 서명한다.
 *
 * <p>비대칭을 쓰는 이유는 검증자인 게이트웨이가 유일한 퍼블릭 인그레스이기 때문이다. 대칭 키라면
 * 검증 키가 곧 발급 키이므로 게이트웨이 침해가 임의 신원 위조가 된다. 공개키만 나눠주면 침해당해도
 * 검증만 가능하다 (ADR-0007).
 *
 * <p>담는 값은 회원 식별자와 시각뿐이다. 서명은 위조를 막을 뿐 내용을 감추지 않으므로 담은 것은
 * 모두 읽힌다. 권한을 담지 않는 이유는 따로 있다 — 담으면 폐기 지연이 권한 변경에도 적용된다.
 */
@Component
public class Es256AccessTokenIssuer implements AccessTokenIssuer {
    private final ECPrivateKey privateKey;
    private final Clock clock;
    private final Duration ttl;
    private final String issuer;

    public Es256AccessTokenIssuer(
            Clock clock,
            @Value("${member.access-token.private-key}") String encodedPrivateKey,
            @Value("${member.access-token.ttl}") Duration ttl,
            @Value("${member.access-token.issuer}") String issuer
    ) {
        this.clock = clock;
        this.ttl = ttl;
        this.issuer = issuer;
        this.privateKey = readPrivateKey(encodedPrivateKey);
    }

    @Override
    public AccessToken issue(String memberId) {
        var issuedAt = clock.instant();
        var expiresAt = issuedAt.plus(ttl);
        var claims = new JWTClaimsSet.Builder()
                .subject(memberId)
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException failure) {
            throw new IllegalStateException("access token signing failed", failure);
        }
        return new AccessToken(jwt.serialize(), expiresAt);
    }

    private static ECPrivateKey readPrivateKey(String encoded) {
        try {
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded));
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (RuntimeException | java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("member.access-token.private-key is not a usable EC private key", failure);
        }
    }
}
