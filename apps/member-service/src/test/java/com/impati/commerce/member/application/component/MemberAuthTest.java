package com.impati.commerce.member.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.in.RegistrationUseCase;
import com.impati.commerce.member.application.port.in.SessionUseCase;
import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.member.application.port.out.SecureTokens;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verifyNoInteractions;

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
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-auth;DB_CLOSE_DELAY=-1",
        // 아웃박스 발송기를 멈춘다. 이 테스트는 아웃박스에 적힌 원문 토큰을 읽어 인증
        // 흐름을 확인하는데, 발송기가 그 사이에 보내면 토큰이 지워져 읽을 수 없다.
        // 컨텍스트는 JVM 수명 내내 살아 있으므로 다른 테스트가 도는 동안에도 계속 틴다.
        // 간격을 늘려도 기동 직후 한 번은 돈다 — 그때 아웃박스가 비어 있어 무해할 뿐이다.
        "member.verification-mail-dispatch-interval=3600000"
})
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

    /**
     * [PD-0001-R1] 가입 직후 상태가 미인증인 것을 잡는다. 그 상태로 로그인이 막히는지는 보지 않는다.
     *
     * <p>메일은 보내지 않고 아웃박스에만 적는다. 가입 트랜잭션에서 나가는 호출이 없다는 것이
     * BL-0004가 고친 것이므로, 알림 클라이언트가 불리지 않았다는 쪽을 함께 단정한다 —
     * 여기서 부르면 알림 서비스의 응답 시간이 다시 트랜잭션 길이가 된다.
     */
    @Test
    void registerLeavesMemberUnverifiedAndQueuesMail() {
        var member = registrationUseCase.register("flow@impati.dev", "Flow", "flow-password");

        assertThat(member.status()).isEqualTo("PENDING_VERIFICATION");
        verifyNoInteractions(notificationClient);
        var row = jdbc.queryForMap(
                "select email, status, attempts from verification_mails where member_id = ?", member.id());
        assertThat(row.get("EMAIL")).isEqualTo("flow@impati.dev");
        assertThat(row.get("STATUS")).isEqualTo("PENDING");
        assertThat(row.get("ATTEMPTS")).isEqualTo(0);
    }

    /**
     * [PD-0001-R1] 확인을 마치면 로그인이 되는 것을 잡는다. 확인 없이 막히는 쪽은 아래 테스트가 본다.
     *
     * <p>로그인이 세션 토큰과 접근 토큰을 함께 내주는 것까지 본다. 접근 토큰이 없으면 클라이언트가
     * 첫 요청부터 갱신을 해야 한다.
     */
    @Test
    void verifiedMemberCanLoginAndRefresh() {
        var member = registrationUseCase.register("login@impati.dev", "Login", "login-password");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));

        var login = sessionUseCase.login("login@impati.dev", "login-password");

        assertThat(login.accessToken()).isNotBlank();
        assertThat(sessionUseCase.refresh(login.sessionToken()).accessToken()).isNotBlank();
        assertThat(member.id()).isNotBlank();
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

    /** [PD-0014-R4] 만료된 세션으로는 갱신되지 않는다. 사용해도 만료가 연장되지 않는지는 보지 않는다. */
    @Test
    void expiredSessionDoesNotRefresh() {
        var member = registrationUseCase.register("session@impati.dev", "Session", "session-pw12");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));
        var login = sessionUseCase.login("session@impati.dev", "session-pw12");

        clock.advance(Duration.ofDays(15));

        assertThatThrownBy(() -> sessionUseCase.refresh(login.sessionToken()))
                .isInstanceOf(DomainException.class);
    }

    /**
     * [PD-0014-R5] 폐기된 세션이 만료 전이라도 확인되지 않는 것을 잡는다. 다른 기기의 세션은 보지 않는다.
     *
     * <p>세션이 폐기되면 더 이상 갱신되지 않는다는 것까지만 잡는다. <b>이미 발급된 접근 토큰이
     * 만료까지 통하는 것은 여기서 보이지 않는다</b> — 그 상한(R8)은 게이트웨이가 검증하는
     * 값이므로 게이트웨이 테스트가 고정한다.
     */
    @Test
    void logoutStopsFurtherRefresh() {
        var member = registrationUseCase.register("logout@impati.dev", "Logout", "logout-pw123");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));
        var login = sessionUseCase.login("logout@impati.dev", "logout-pw123");

        sessionUseCase.logout(login.sessionToken());

        assertThatThrownBy(() -> sessionUseCase.refresh(login.sessionToken()))
                .isInstanceOf(DomainException.class);
    }

    /**
     * [PD-0014-R8] 접근 토큰의 수명이 5분이다. 그 수명이 곧 정상 동작 중의 폐기 반영 상한이다.
     *
     * <p>설정값을 덮어쓰지 않고 운영과 같은 값으로 확인한다 — 덮어쓰면 설정이 바뀌어도 통과해
     * 정책이 정한 수를 아무것도 고정하지 못한다.
     *
     * <p>발급 시각과 만료 시각의 차를 본다. 절대 시각을 보면 테스트가 도는 시점에 좌우된다.
     */
    @Test
    void accessTokenLivesForFiveMinutes() throws Exception {
        var member = registrationUseCase.register("ttl@impati.dev", "Ttl", "ttl-password1");
        registrationUseCase.verifyEmail(rawTokenOf(member.id()));

        var login = sessionUseCase.login("ttl@impati.dev", "ttl-password1");

        var claims = com.nimbusds.jwt.SignedJWT.parse(login.accessToken()).getJWTClaimsSet();
        var lifetime = Duration.between(
                claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant());
        assertThat(lifetime).isEqualTo(Duration.ofMinutes(5));
    }

    /**
     * 인증 테이블과 세션 테이블에는 해시만 있어야 한다.
     *
     * <p>아웃박스는 예외이며 그것이 BL-0004가 만든 유일한 새 노출이다. 발송을 나중으로 미루면
     * 보낼 원문을 어딘가 들고 있어야 하기 때문이다. 대신 종단 상태가 되면 지운다 —
     * 그것을 확인하는 것은 {@code VerificationMailDispatchTest}다.
     */
    @Test
    void storesOnlyHashedTokensOutsideTheOutbox() {
        var member = registrationUseCase.register("hash@impati.dev", "Hash", "hash-password");
        var raw = rawTokenOf(member.id());
        registrationUseCase.verifyEmail(raw);
        var login = sessionUseCase.login("hash@impati.dev", "hash-password");

        assertThat(countVerifications(raw)).isZero();
        assertThat(countVerifications(secureTokens.hash(raw))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from member_sessions where token_hash = ?", Integer.class, login.sessionToken()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from member_sessions where token_hash = ?",
                Integer.class,
                secureTokens.hash(login.sessionToken())))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from verification_mails where token = ?", Integer.class, raw))
                .isEqualTo(1);
    }

    private Integer countVerifications(String tokenHash) {
        return jdbc.queryForObject(
                "select count(*) from email_verifications where token_hash = ?", Integer.class, tokenHash);
    }

    /**
     * 발급된 원문 토큰을 가져온다.
     *
     * <p>서비스는 원문을 돌려주지 않고 인증 테이블에는 해시만 남으므로, 보내려고 아웃박스에
     * 적어둔 값을 읽는다. 운영에서 토큰을 알 수 있는 경로가 메일뿐이라는 사실과 같은 구조다 —
     * 여기서 읽는 것이 곧 메일에 실릴 값이다.
     *
     * <p>재발송은 항목을 하나 더 만들므로 가장 최근 것을 가져온다.
     */
    private String rawTokenOf(String memberId) {
        return jdbc.queryForObject(
                "select token from verification_mails where member_id = ? order by seq desc limit 1",
                String.class,
                memberId
        );
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
