package com.impati.commerce.member.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.in.IssuedSession;
import com.impati.commerce.member.application.port.in.IssuedAccessToken;
import com.impati.commerce.member.application.port.in.SessionUseCase;
import com.impati.commerce.member.application.port.out.AccessTokenIssuer;
import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.application.port.out.PasswordHasher;
import com.impati.commerce.member.application.port.out.SecureTokens;
import com.impati.commerce.member.application.port.out.SessionRepository;
import com.impati.commerce.member.domain.MemberModels.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * 로그인, 접근 토큰 갱신, 폐기.
 *
 * <p>세션은 장수명 자격증명이고 접근 토큰은 그 세션에서 파생되는 단명 증명서다. 게이트웨이는
 * 접근 토큰을 스스로 검증하므로 요청마다 여기를 부르지 않는다 — {@link #refresh}만 접근 토큰
 * 수명당 한 번 호출된다 (ADR-0007).
 *
 * <p>폐기는 세션에만 건다. 이미 발급된 접근 토큰은 만료까지 통하므로 접근 토큰 수명이 곧 폐기
 * 지연 상한이다 (PD-0014-R5, PD-0014-R8).
 */
@Component
public class SessionExecutor implements SessionUseCase {
    private final MemberRepository memberRepository;
    private final SessionRepository sessionRepository;
    private final PasswordHasher passwordHasher;
    private final SecureTokens secureTokens;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;
    private final Duration sessionTtl;

    public SessionExecutor(
            MemberRepository memberRepository,
            SessionRepository sessionRepository,
            PasswordHasher passwordHasher,
            SecureTokens secureTokens,
            AccessTokenIssuer accessTokenIssuer,
            Clock clock,
            @Value("${member.session-ttl}") Duration sessionTtl
    ) {
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.passwordHasher = passwordHasher;
        this.secureTokens = secureTokens;
        this.accessTokenIssuer = accessTokenIssuer;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    /**
     * 로그인.
     *
     * <p>이메일이 없을 때와 비밀번호가 틀렸을 때를 같은 메시지로 거절한다. 구분해서 알려주면
     * 어떤 이메일이 가입돼 있는지 열거할 수 있다.
     */
    @Transactional
    @Override
    public IssuedSession login(String email, String rawPassword) {
        var member = memberRepository.findByEmail(email)
                .orElseThrow(() -> DomainException.validation("email or password is incorrect"));
        if (!passwordHasher.matches(rawPassword, member.passwordHash())) {
            throw DomainException.validation("email or password is incorrect");
        }
        if (!member.isActive()) {
            throw DomainException.conflict("email is not verified");
        }

        var rawToken = secureTokens.newToken();
        var expiresAt = clock.instant().plus(sessionTtl);
        sessionRepository.save(new Session(secureTokens.hash(rawToken), member.id(), expiresAt));
        var accessToken = accessTokenIssuer.issue(member.id());
        return new IssuedSession(
                rawToken,
                expiresAt.toString(),
                accessToken.value(),
                accessToken.expiresAt().toString()
        );
    }

    /**
     * 세션을 확인하고 새 접근 토큰을 발급한다.
     *
     * <p>저장소를 보는 유일한 인증 경로다. 폐기가 실제로 반영되는 지점이므로 여기서 거절되면
     * 사용자는 다시 로그인해야 한다. 이유를 구분하지 않는 것은 정해진 규칙이다 (PD-0014-R7).
     */
    @Transactional(readOnly = true)
    @Override
    public IssuedAccessToken refresh(String rawSessionToken) {
        var session = sessionRepository.findByTokenHash(secureTokens.hash(rawSessionToken))
                .filter(candidate -> candidate.isUsable(clock.instant()))
                .orElseThrow(() -> DomainException.notFound("session is not valid"));
        var accessToken = accessTokenIssuer.issue(session.memberId());
        return new IssuedAccessToken(accessToken.value(), accessToken.expiresAt().toString());
    }

    @Transactional
    @Override
    public void logout(String rawToken) {
        sessionRepository.findByTokenHash(secureTokens.hash(rawToken)).ifPresent(session -> {
            session.revoke(clock.instant());
            sessionRepository.save(session);
        });
    }
}
