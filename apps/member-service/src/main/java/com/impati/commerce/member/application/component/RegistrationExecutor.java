package com.impati.commerce.member.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.in.MemberDetails;
import com.impati.commerce.member.application.port.in.RegistrationUseCase;
import com.impati.commerce.member.application.port.out.EmailVerificationRepository;
import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.application.port.out.PasswordHasher;
import com.impati.commerce.member.application.port.out.SecureTokens;
import com.impati.commerce.member.application.port.out.VerificationMailRepository;
import com.impati.commerce.member.domain.MemberModels.EmailVerification;
import com.impati.commerce.member.domain.MemberModels.Member;
import com.impati.commerce.member.domain.MemberModels.VerificationMail;
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
 *
 * <p>알림 서비스를 부르지 않는다. 발송할 것을 아웃박스에 적는 데까지가 여기의 일이고, 실제
 * 발송은 {@link VerificationMailDispatchExecutor}가 가져간다 (ADR-0010).
 */
@Component
public class RegistrationExecutor implements RegistrationUseCase {
    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordHasher passwordHasher;
    private final SecureTokens secureTokens;
    private final VerificationMailRepository verificationMailRepository;
    private final Clock clock;
    private final Duration verificationTtl;

    public RegistrationExecutor(
            MemberRepository memberRepository,
            EmailVerificationRepository emailVerificationRepository,
            PasswordHasher passwordHasher,
            SecureTokens secureTokens,
            VerificationMailRepository verificationMailRepository,
            Clock clock,
            @Value("${member.verification-ttl}") Duration verificationTtl
    ) {
        this.memberRepository = memberRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordHasher = passwordHasher;
        this.secureTokens = secureTokens;
        this.verificationMailRepository = verificationMailRepository;
        this.clock = clock;
        this.verificationTtl = verificationTtl;
    }

    /**
     * 가입은 이메일 소유가 확인되지 않은 상태로 끝난다. 로그인은 확인 후에만 된다.
     *
     * <p>메일 시스템 장애로 가입을 막지 않는다. 그러나 실패를 없던 일로 하지도 않는다 —
     * 보낼 것을 아웃박스에 함께 커밋하므로 발송은 나중에라도 일어난다.
     */
    @Transactional
    @Override
    public MemberDetails register(String email, String name, String rawPassword) {
        memberRepository.findByEmail(email).ifPresent(existing -> {
            throw DomainException.conflict("member email already exists");
        });
        requirePassword(rawPassword);

        var member = new Member(email, name, passwordHasher.hash(rawPassword));
        memberRepository.save(member);
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
        var verification = emailVerificationRepository.findByTokenHash(secureTokens.hash(rawToken))
                .orElseThrow(() -> DomainException.validation("verification token is not usable"));
        verification.use(clock.instant());
        emailVerificationRepository.save(verification);

        var member = getMember(verification.memberId());
        member.activate();
        memberRepository.save(member);
        return MemberMapper.toDetails(member);
    }

    /**
     * 확인 토큰을 발급하고 보낼 것을 아웃박스에 적는다.
     *
     * <p>둘이 호출자의 트랜잭션에서 함께 커밋된다. 그래서 "확인 토큰은 있는데 아무도 보내지
     * 않는" 상태도, "메일은 보냈는데 토큰이 없는" 상태도 생기지 않는다.
     *
     * <p>여기서 나가는 호출이 없다는 것이 이 메서드의 핵심이다. 알림 서비스가 얼마나 느리든
     * 가입 트랜잭션의 길이에 영향을 주지 않는다 (ADR-0010).
     */
    private void issueVerification(Member member) {
        var rawToken = secureTokens.newToken();
        emailVerificationRepository.save(new EmailVerification(
                secureTokens.hash(rawToken),
                member.id(),
                clock.instant().plus(verificationTtl)
        ));
        verificationMailRepository.save(new VerificationMail(member.id(), member.email(), rawToken));
    }

    private void requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw DomainException.validation("password must be at least 8 characters");
        }
    }

    private Member getMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found"));
    }
}
