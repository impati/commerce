package com.impati.commerce.member.application;

import com.impati.commerce.common.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * 가입 → 이메일 인증 → 로그인 경로를 검증한다.
 *
 * <p>흐름이 {@link RegistrationService}와 {@link SessionService} 두 클래스에 걸쳐 있으므로 둘을 함께
 * 주입한다. 클래스가 나뉘어도 사용자가 겪는 경로는 하나이며, 그 경로가 이 테스트의 대상이다.
 *
 * <p>만료를 확인하려면 시간을 앞으로 돌릴 수 있어야 하므로 {@link Clock}을 테스트가 조작하는
 * 구현으로 바꾼다. {@code Thread.sleep}으로 기다리는 테스트는 느리고 불안정하다.
 *
 * <p>테스트가 같은 DB를 공유하므로 이메일을 테스트별로 다르게 쓴다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:member-auth;DB_CLOSE_DELAY=-1")
class MemberAuthTest {
    /** 테스트가 앞으로 돌릴 수 있는 시계. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-25T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private SessionService sessions;

    @Autowired
    private SecureTokens tokens;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private NotificationClient notifications;

    @Test
    void registerLeavesMemberUnverifiedAndRequestsMail() {
        var member = registrations.register("flow@impati.dev", "Flow", "flow-password");

        assertThat(member.status()).isEqualTo("PENDING_VERIFICATION");
        verify(notifications).requestEmailVerification(eq(member.id()), eq("flow@impati.dev"), anyString());
    }

    @Test
    void verifiedMemberCanLoginAndSessionResolves() {
        var member = registrations.register("login@impati.dev", "Login", "login-password");
        registrations.verifyEmail(rawTokenOf(member.id()));

        var login = sessions.login("login@impati.dev", "login-password");

        assertThat(sessions.resolveSession(login.token()).memberId()).isEqualTo(member.id());
    }

    /** 인증 전에는 로그인할 수 없다. 이메일 소유가 확인되지 않은 계정이다. */
    @Test
    void unverifiedMemberCannotLogin() {
        registrations.register("unverified@impati.dev", "Unverified", "unverified-pw");

        assertThatThrownBy(() -> sessions.login("unverified@impati.dev", "unverified-pw"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not verified");
    }

    /** 인증 토큰은 단일 사용이다. */
    @Test
    void verificationTokenCannotBeReused() {
        var member = registrations.register("reuse@impati.dev", "Reuse", "reuse-password");
        var raw = rawTokenOf(member.id());
        registrations.verifyEmail(raw);

        assertThatThrownBy(() -> registrations.verifyEmail(raw))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not usable");
    }

    /** 만료된 토큰으로는 인증되지 않는다. 재사용과 같은 메시지로 거절한다. */
    @Test
    void expiredVerificationTokenIsRejected() {
        var member = registrations.register("expired@impati.dev", "Expired", "expired-pw");
        var raw = rawTokenOf(member.id());

        clock.advance(Duration.ofDays(2));

        assertThatThrownBy(() -> registrations.verifyEmail(raw))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not usable");
    }

    /** 없는 이메일과 틀린 비밀번호를 같은 메시지로 거절한다. 이메일 열거를 막는다. */
    @Test
    void wrongPasswordAndUnknownEmailFailIdentically() {
        var member = registrations.register("same@impati.dev", "Same", "same-password");
        registrations.verifyEmail(rawTokenOf(member.id()));

        var wrongPassword = catchMessage(() -> sessions.login("same@impati.dev", "not-the-password"));
        var unknownEmail = catchMessage(() -> sessions.login("absent@impati.dev", "not-the-password"));

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void expiredSessionDoesNotResolve() {
        var member = registrations.register("session@impati.dev", "Session", "session-pw12");
        registrations.verifyEmail(rawTokenOf(member.id()));
        var login = sessions.login("session@impati.dev", "session-pw12");

        clock.advance(Duration.ofDays(15));

        assertThatThrownBy(() -> sessions.resolveSession(login.token()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void logoutRevokesSessionImmediately() {
        var member = registrations.register("logout@impati.dev", "Logout", "logout-pw123");
        registrations.verifyEmail(rawTokenOf(member.id()));
        var login = sessions.login("logout@impati.dev", "logout-pw123");

        sessions.logout(login.token());

        assertThatThrownBy(() -> sessions.resolveSession(login.token()))
                .isInstanceOf(DomainException.class);
    }

    /** 원문 토큰은 저장되지 않는다. DB에는 해시만 있어야 한다. */
    @Test
    void storesOnlyHashedTokens() {
        var member = registrations.register("hash@impati.dev", "Hash", "hash-password");
        var raw = rawTokenOf(member.id());
        registrations.verifyEmail(raw);
        var login = sessions.login("hash@impati.dev", "hash-password");

        assertThat(countVerifications(raw)).isZero();
        assertThat(countVerifications(tokens.hash(raw))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from member_sessions where token_hash = ?", Integer.class, login.token()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from member_sessions where token_hash = ?",
                Integer.class,
                tokens.hash(login.token())))
                .isEqualTo(1);
    }

    private Integer countVerifications(String tokenHash) {
        return jdbc.queryForObject(
                "select count(*) from email_verifications where token_hash = ?", Integer.class, tokenHash);
    }

    /**
     * 발급된 원문 토큰을 가져온다.
     *
     * <p>서비스는 원문을 돌려주지 않고 해시만 저장하므로 테스트는 메일 요청으로 넘어간 값을
     * 가로챈다. 운영에서 토큰을 알 수 있는 경로가 메일뿐이라는 사실과 같은 구조다.
     */
    private String rawTokenOf(String memberId) {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(notifications, atLeastOnce())
                .requestEmailVerification(eq(memberId), anyString(), captor.capture());
        return captor.getValue();
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected failure");
        } catch (DomainException expected) {
            return expected.getMessage();
        }
    }
}
