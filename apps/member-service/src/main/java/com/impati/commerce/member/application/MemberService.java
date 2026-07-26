package com.impati.commerce.member.application;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.LoginResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.SessionResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.EmailVerification;
import com.impati.commerce.member.domain.MemberModels.Member;
import com.impati.commerce.member.domain.MemberModels.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class MemberService {
    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepository members;
    private final EmailVerificationRepository verifications;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SecureTokens tokens;
    private final NotificationClient notifications;
    private final Clock clock;
    private final Duration verificationTtl;
    private final Duration sessionTtl;

    public MemberService(
            MemberRepository members,
            EmailVerificationRepository verifications,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            SecureTokens tokens,
            NotificationClient notifications,
            Clock clock,
            @Value("${member.verification-ttl}") Duration verificationTtl,
            @Value("${member.session-ttl}") Duration sessionTtl
    ) {
        this.members = members;
        this.verifications = verifications;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.tokens = tokens;
        this.notifications = notifications;
        this.clock = clock;
        this.verificationTtl = verificationTtl;
        this.sessionTtl = sessionTtl;
    }

    /**
     * 가입은 이메일 소유가 확인되지 않은 상태로 끝난다. 로그인은 확인 후에만 된다.
     *
     * <p>메일 요청이 실패해도 가입은 유지한다. 메일 시스템 장애로 가입을 막을 이유가 없고,
     * 사용자는 재발송으로 복구할 수 있다.
     *
     * <p>TODO 이 호출이 트랜잭션 안에 있어 상대가 느리면 DB 트랜잭션이 함께 늘어난다.
     * notification-service에 만든 것과 같은 아웃박스가 필요하다. NEXT.md 우선순위 3.
     */
    @Transactional
    public MemberResponse register(String email, String name, String rawPassword) {
        members.findByEmail(email).ifPresent(existing -> {
            throw DomainException.conflict("member email already exists");
        });
        requirePassword(rawPassword);

        var member = new Member(email, name, passwordHasher.hash(rawPassword));
        members.save(member);
        issueVerification(member);
        return MemberMapper.toResponse(member);
    }

    /** 인증 메일을 다시 보낸다. 이전 토큰은 그대로 두고 새 토큰을 발급한다. */
    @Transactional
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
    public MemberResponse verifyEmail(String rawToken) {
        var verification = verifications.findByTokenHash(tokens.hash(rawToken))
                .orElseThrow(() -> DomainException.validation("verification token is not usable"));
        verification.use(clock.instant());
        verifications.save(verification);

        var member = getMember(verification.memberId());
        member.activate();
        members.save(member);
        return MemberMapper.toResponse(member);
    }

    /**
     * 로그인.
     *
     * <p>이메일이 없을 때와 비밀번호가 틀렸을 때를 같은 메시지로 거절한다. 구분해서 알려주면
     * 어떤 이메일이 가입돼 있는지 열거할 수 있다.
     */
    @Transactional
    public LoginResponse login(String email, String rawPassword) {
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
        return new LoginResponse(rawToken, expiresAt.toString());
    }

    /** 세션이 가리키는 회원을 돌려준다. 게이트웨이가 신원을 확인할 때 쓴다. */
    @Transactional(readOnly = true)
    public SessionResponse resolveSession(String rawToken) {
        var session = sessions.findByTokenHash(tokens.hash(rawToken))
                .filter(candidate -> candidate.isUsable(clock.instant()))
                .orElseThrow(() -> DomainException.notFound("session is not valid"));
        return new SessionResponse(session.memberId());
    }

    @Transactional
    public void logout(String rawToken) {
        sessions.findByTokenHash(tokens.hash(rawToken)).ifPresent(session -> {
            session.revoke(clock.instant());
            sessions.save(session);
        });
    }

    /** 로컬 데모용 시드. 이메일 확인을 건너뛰고 바로 활성 상태로 만든다. */
    @Transactional
    public MemberResponse seed(String memberId, String email, String name, String rawPassword) {
        var existing = members.findByEmail(email);
        if (existing.isPresent()) {
            return MemberMapper.toResponse(existing.get());
        }
        var member = new Member(memberId, email, name, passwordHasher.hash(rawPassword));
        member.activate();
        members.save(member);
        return MemberMapper.toResponse(member);
    }

    @Transactional
    public AddressResponse addAddress(
            String memberId,
            String alias,
            String recipient,
            String phone,
            String line1,
            String city,
            String postalCode,
            boolean defaultAddress
    ) {
        var member = getMember(memberId);
        var address = new Address(alias, recipient, phone, line1, city, postalCode, defaultAddress);
        member.addAddress(address);
        members.save(member);
        return MemberMapper.toResponse(address);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(String memberId) {
        return MemberMapper.toResponse(getMember(memberId));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list() {
        return members.findAll().stream().map(MemberMapper::toResponse).toList();
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
