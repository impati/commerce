package com.impati.commerce.gateway.support;

import com.impati.commerce.common.DomainException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.util.Base64;

/**
 * 접근 토큰을 회원 신원으로 바꾼다.
 *
 * <p>토큰 검증 지식은 게이트웨이에만 있다. 하위 서비스는 토큰을 모르고 {@code X-Member-Id}만
 * 신뢰한다. 그래서 하위 서비스는 게이트웨이 뒤에만 있어야 한다 — 직접 노출되면 헤더를 위조해
 * 아무 회원으로 행세할 수 있다. 이 경계는 코드가 아니라 배포 토폴로지가 강제한다: 게이트웨이만
 * 퍼블릭 인그레스이고 나머지는 프라이빗망에 둔다 (ADR-0002).
 *
 * <p><b>member-service를 부르지 않는다.</b> 접근 토큰은 서명되어 있으므로 공개키만으로 검증된다.
 * 그래서 member-service가 죽어도 토큰이 살아 있는 동안은 인증이 동작한다. 대가로 세션을 폐기해도
 * 이미 발급된 토큰은 만료까지 통한다 — 그 수명이 폐기 지연 상한이다 (ADR-0007, PD-0014-R8).
 *
 * <p>공개키만 갖는 이유는 이 서비스가 유일한 퍼블릭 인그레스이기 때문이다. 대칭 키라면 검증 키가
 * 곧 발급 키이므로 여기가 뚫리면 임의 신원을 위조할 수 있다.
 *
 * <p>TODO 접근 토큰이 만료된 뒤에는 갱신이 필요하고 그 경로는 member-service에 의존한다. 즉 장애
 * 내성이 접근 토큰 수명(5분)까지다. 장애 판정과 더 긴 상한은 BL-0045.
 */
@Component
public class MemberIdentity {
    private static final String BEARER = "Bearer ";

    private final ECPublicKey publicKey;
    private final Clock clock;
    private final String issuer;

    public MemberIdentity(
            Clock clock,
            @Value("${gateway.access-token.public-key}") String encodedPublicKey,
            @Value("${gateway.access-token.issuer}") String issuer
    ) {
        this.clock = clock;
        this.issuer = issuer;
        this.publicKey = readPublicKey(encodedPublicKey);
    }

    /**
     * 인증이 필요한 경로에서 쓴다. 토큰이 없거나 유효하지 않으면 401이다.
     *
     * <p>서명, 발급자, 만료를 모두 확인한다. 서명만 보면 다른 용도로 발급된 토큰이 통하고,
     * 만료를 보지 않으면 폐기 지연 상한이 사라진다.
     */
    public String require(String authorizationHeader) {
        var token = bearerToken(authorizationHeader);
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException malformed) {
            throw unauthorized();
        }
        if (!verified(jwt) || !issuedByUs(jwt) || expired(jwt)) {
            throw unauthorized();
        }
        return subject(jwt);
    }

    private boolean verified(SignedJWT jwt) {
        try {
            return jwt.verify(new ECDSAVerifier(publicKey));
        } catch (JOSEException unusable) {
            return false;
        }
    }

    private boolean issuedByUs(SignedJWT jwt) {
        return issuer.equals(claims(jwt).getIssuer());
    }

    private boolean expired(SignedJWT jwt) {
        var expiresAt = claims(jwt).getExpirationTime();
        return expiresAt == null || !clock.instant().isBefore(expiresAt.toInstant());
    }

    private String subject(SignedJWT jwt) {
        var subject = claims(jwt).getSubject();
        if (subject == null || subject.isBlank()) {
            throw unauthorized();
        }
        return subject;
    }

    private com.nimbusds.jwt.JWTClaimsSet claims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException malformed) {
            throw unauthorized();
        }
    }

    public String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            throw unauthorized();
        }
        var token = authorizationHeader.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            throw unauthorized();
        }
        return token;
    }

    private DomainException unauthorized() {
        return new DomainException("unauthorized", "authentication is required", 401);
    }

    private static ECPublicKey readPublicKey(String encoded) {
        try {
            var spec = new X509EncodedKeySpec(Base64.getDecoder().decode(encoded));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (RuntimeException | java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("gateway.access-token.public-key is not a usable EC public key", failure);
        }
    }
}
