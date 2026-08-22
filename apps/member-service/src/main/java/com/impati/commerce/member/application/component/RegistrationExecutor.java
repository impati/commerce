package com.impati.commerce.member.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.in.MemberDetails;
import com.impati.commerce.member.application.port.in.RegistrationUseCase;
import com.impati.commerce.member.application.port.out.EmailVerificationRepository;
import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.member.application.port.out.PasswordHasher;
import com.impati.commerce.member.application.port.out.SecureTokens;
import com.impati.commerce.member.domain.MemberModels.EmailVerification;
import com.impati.commerce.member.domain.MemberModels.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * 가입과 이메일 소유 확인.
 *
 * <p>세션은 다루지 않는다. 가입이 로그인시켜주지 않으므로 {@link SessionService}와 의존성이
 * 겹치지 않는다.
 */
@Component
public class RegistrationExecutor implements RegistrationUseCase {
    private static final Logger log = LoggerFactory.getLogger(RegistrationExecutor.class);

    private final MemberRepository members;
    private final EmailVerificationRepository verifications;
    private final PasswordHasher passwordHasher;
    private final SecureTokens tokens;
    private final NotificationClient notifications;
    private final Clock clock;
    private final Duration verificationTtl;

    public RegistrationExecutor(
            MemberRepository members,
            EmailVerificationRepository verifications,
            PasswordHasher passwordHasher,
            SecureTokens tokens,
            NotificationClient notifications,
            Clock clock,
            @Value("${member.verification-ttl}") Duration verificationTtl
    ) {
        this.members = members;
        this.verifications = verifications;
        this.passwordHasher = passwordHasher;
        this.tokens = tokens;
        this.notifications = notifications;
        this.clock = clock;
        this.verificationTtl = verificationTtl;
    }

    /**
     * 가입은 이메일 소유가 확인되지 않은 상태로 끝난다. 로그인은 확인 후에만 된다.
     *
     * <p>메일 요청이 실패해도 가입은 유지한다. 메일 시스템 장애로 가입을 막을 이유가 없고,
     * 사용자는 재발송으로 복구할 수 있다.
     *
     * <p>TODO 이 호출이 트랜잭션 안에 있어 상대가 느리면 DB 트랜잭션이 함께 늘어난다.
     * notification-service에 만든 것과 같은 아웃박스가 필요하다. BL-0004.
     */
    @Transactional
    @Override
    public MemberDetails register(String email, String name, String rawPassword) {
        members.findByEmail(email).ifPresent(existing -> {
            throw DomainException.conflict("member email already exists");
        });
        requirePassword(rawPassword);

        var member = new Member(email, name, passwordHasher.hash(rawPassword));
        members.save(member);
        issueVerification(member);
        return MemberMapper.toDetails(member);
    }

    /** 인증 메일을 다시 보낸다. 이전 토큰은 그대로 두고 새 토큰을 발급한다. */
    @Transactional
    @Override
    public void resendVerification(String memberId) {
        var member = getMember(memberId);
        if (member.isActive()) {
            throw DomainException.conflict("member is already verified");
        }
        issueVerification(member);
    }

    /**
     * 이메일 소유를 확인한다.
     *
     * <p>토큰은 단일 사용이며 만료가 있다. 이미 쓴 토큰과 만료된 토큰을 같은 메시지로 거절한다.
     */
    @Transactional
    @Override
    public MemberDetails verifyEmail(String rawToken) {
        var verification = verifications.findByTokenHash(tokens.hash(rawToken))
                .orElseThrow(() -> DomainException.validation("verification token is not usable"));
        verification.use(clock.instant());
        verifications.save(verification);

        var member = getMember(verification.memberId());
        member.activate();
        members.save(member);
        return MemberMapper.toDetails(member);
    }

    private void issueVerification(Member member) {
        var rawToken = tokens.newToken();
        verifications.save(new EmailVerification(
                tokens.hash(rawToken),
                member.id(),
                clock.instant().plus(verificationTtl)
        ));
        try {
            notifications.requestEmailVerification(member.id(), member.email(), rawToken);
        } catch (RuntimeException failure) {
            log.warn("verification mail request failed memberId={} reason={}", member.id(), failure.getMessage());
        }
    }

    private void requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw DomainException.validation("password must be at least 8 characters");
        }
    }

    private Member getMember(String memberId) {
        return members.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found"));
    }
}
