package com.impati.commerce.member.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.in.IssuedSession;
import com.impati.commerce.member.application.port.in.SessionOwner;
import com.impati.commerce.member.application.port.in.SessionUseCase;
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
 * 로그인, 세션 확인, 폐기.
 *
 * <p>{@link #resolveSession}은 이 시스템에서 가장 자주 호출되는 경로다 — 인증이 필요한 모든 요청이
 * 게이트웨이를 지나며 한 번씩 부른다. 캐시·서킷브레이커·지표를 붙일 대상이 이 클래스이므로
 * 배송지 수정 같은 저빈도 기능과 같은 클래스에 두지 않는다.
 */
@Component
public class SessionExecutor implements SessionUseCase {
    private final MemberRepository members;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecureTokens tokens;
    private final Clock clock;
    private final Duration sessionTtl;

    public SessionExecutor(
            MemberRepository members,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            SecureTokens tokens,
            Clock clock,
            @Value("${member.session-ttl}") Duration sessionTtl
    ) {
        this.members = members;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.tokens = tokens;
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
        var member = members.findByEmail(email)
                .orElseThrow(() -> DomainException.validation("email or password is incorrect"));
        if (!passwordHasher.matches(rawPassword, member.passwordHash())) {
            throw DomainException.validation("email or password is incorrect");
        }
        if (!member.isActive()) {
            throw DomainException.conflict("email is not verified");
        }

        var rawToken = tokens.newToken();
        var expiresAt = clock.instant().plus(sessionTtl);
        sessions.save(new Session(tokens.hash(rawToken), member.id(), expiresAt));
        return new IssuedSession(rawToken, expiresAt.toString());
    }

    /** 세션이 가리키는 회원을 돌려준다. 게이트웨이가 신원을 확인할 때 쓴다. */
    @Transactional(readOnly = true)
    @Override
    public SessionOwner resolveSession(String rawToken) {
        var session = sessions.findByTokenHash(tokens.hash(rawToken))
                .filter(candidate -> candidate.isUsable(clock.instant()))
                .orElseThrow(() -> DomainException.notFound("session is not valid"));
        return new SessionOwner(session.memberId());
    }

    @Transactional
    @Override
    public void logout(String rawToken) {
        sessions.findByTokenHash(tokens.hash(rawToken)).ifPresent(session -> {
            session.revoke(clock.instant());
            sessions.save(session);
        });
    }
}
