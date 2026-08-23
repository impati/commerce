package com.impati.commerce.member.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.in.RegistrationUseCase;
import com.impati.commerce.member.application.port.in.SessionUseCase;
import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.member.application.port.out.SecureTokens;
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
 * <p>흐름이 {@link RegistrationUseCase}와 {@link SessionUseCase} 두 클래스에 걸쳐 있으므로 둘을 함께
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
    private RegistrationUseCase registrationUseCase;

    @Autowired
    private SessionUseCase sessionUseCase;

    @Autowired
    private SecureTokens secureTokens;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private NotificationClient notificationClient;

    /** [PD-0001-R1] 가입 직후 상태가 미인증인 것을 잡는다. 그 상태로 로그인이 막히는지는 보지 않는다. */
    @Test
    void registerLeavesMemberUnverifiedAndRequestsMail() {
        var member = registrationUseCase.register("flow@impati.dev", "Flow", "flow-password");

        assertThat(member.status()).isEqualTo("PENDING_VERIFICATION");
        verify(notificationClient).requestEmailVerification(eq(member.id()), eq("flow@impati.dev"), anyString());
    }

    /** [PD-0001-R1] 확인을 마치면 로그인이 되는 것을 잡는다. 확인 없이 막히는 쪽은 아래 테스트가 본다. */
    @Test
    void verifiedMemberCanLoginAndSessionResolves() {
        var member = registrationUseCase.register("login@impati.dev", "Login", "login-password");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));

        var login = sessionUseCase.login("login@impati.dev", "login-password");

        assertThat(sessionUseCase.resolveSession(login.token()).memberId()).isEqualTo(member.id());
    }

    /**
     * [PD-0001-R1][PD-0014-R2] 인증 전에는 로그인할 수 없다. 이메일 소유가 확인되지 않은 계정이다.
     *
     * <p>거절된다는 것만 잡는다. 이 응답이 다른 거절과 구분되어 미인증 계정의 존재를 드러낸다는
     * 점은 보지 않는다. BL-0022.
     */
    @Test
    void unverifiedMemberCannotLogin() {
        registrationUseCase.register("unverified@impati.dev", "Unverified", "unverified-pw");

        assertThatThrownBy(() -> sessionUseCase.login("unverified@impati.dev", "unverified-pw"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not verified");
    }

    /** [PD-0001-R6][PD-0001-R7] 단일 사용을 잡는다. 만료와 같은 응답인지는 두 테스트를 견줘야 알 수 있다. */
    @Test
    void verificationTokenCannotBeReused() {
        var member = registrationUseCase.register("reuse@impati.dev", "Reuse", "reuse-password");
        var raw = rawTokenOf(member.id());
        registrationUseCase.verifyEmail(raw);

        assertThatThrownBy(() -> registrationUseCase.verifyEmail(raw))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not usable");
    }

    /**
     * [PD-0001-R6][PD-0001-R7] 만료된 토큰으로는 인증되지 않는다. 재사용과 같은 메시지로 거절한다.
     *
     * <p>두 응답을 직접 비교하지 않으므로 한쪽 문구만 바뀌면 잡지 못한다. BL-0021.
     */
    @Test
    void expiredVerificationTokenIsRejected() {
        var member = registrationUseCase.register("expired@impati.dev", "Expired", "expired-pw");
        var raw = rawTokenOf(member.id());

        clock.advance(Duration.ofDays(2));

        assertThatThrownBy(() -> registrationUseCase.verifyEmail(raw))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not usable");
    }

    /**
     * [PD-0014-R1] 없는 이메일과 틀린 비밀번호를 같은 메시지로 거절한다. 이메일 열거를 막는다.
     *
     * <p>두 응답을 직접 비교하므로 한쪽만 바뀌어도 잡힌다. 미인증 계정의 거절은 이 비교에 없다.
     */
    @Test
    void wrongPasswordAndUnknownEmailFailIdentically() {
        var member = registrationUseCase.register("same@impati.dev", "Same", "same-password");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));

        var wrongPassword = catchMessage(() -> sessionUseCase.login("same@impati.dev", "not-the-password"));
        var unknownEmail = catchMessage(() -> sessionUseCase.login("absent@impati.dev", "not-the-password"));

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    /** [PD-0014-R4] 만료된 세션은 확인되지 않는다. 사용해도 만료가 연장되지 않는지는 보지 않는다. */
    @Test
    void expiredSessionDoesNotResolve() {
        var member = registrationUseCase.register("session@impati.dev", "Session", "session-pw12");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));
        var login = sessionUseCase.login("session@impati.dev", "session-pw12");

        clock.advance(Duration.ofDays(15));

        assertThatThrownBy(() -> sessionUseCase.resolveSession(login.token()))
                .isInstanceOf(DomainException.class);
    }

    /**
     * [PD-0014-R5] 폐기된 세션이 만료 전이라도 확인되지 않는 것을 잡는다. 다른 기기의 세션은 보지 않는다.
     *
     * <p>여기서는 폐기가 같은 저장소 안에서 일어나므로 즉시 반영된다. 규칙이 요구하는 것은
     * 즉시가 아니라 상한 안이므로(R8) 이 테스트는 상한을 고정하지 못한다. 게이트웨이가 신원을
     * 확인하는 경로가 바뀌면 그 상한을 고정하는 테스트가 따로 필요하다.
     */
    @Test
    void logoutRevokesSessionImmediately() {
        var member = registrationUseCase.register("logout@impati.dev", "Logout", "logout-pw123");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));
        var login = sessionUseCase.login("logout@impati.dev", "logout-pw123");

        sessionUseCase.logout(login.token());

        assertThatThrownBy(() -> sessionUseCase.resolveSession(login.token()))
                .isInstanceOf(DomainException.class);
    }

    /** 원문 토큰은 저장되지 않는다. DB에는 해시만 있어야 한다. */
    @Test
    void storesOnlyHashedTokens() {
        var member = registrationUseCase.register("hash@impati.dev", "Hash", "hash-password");
        var raw = rawTokenOf(member.id());
        registrationUseCase.verifyEmail(raw);
        var login = sessionUseCase.login("hash@impati.dev", "hash-password");

        assertThat(countVerifications(raw)).isZero();
        assertThat(countVerifications(secureTokens.hash(raw))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from member_sessions where token_hash = ?", Integer.class, login.token()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from member_sessions where token_hash = ?",
                Integer.class,
                secureTokens.hash(login.token())))
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
        verify(notificationClient, atLeastOnce())
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
