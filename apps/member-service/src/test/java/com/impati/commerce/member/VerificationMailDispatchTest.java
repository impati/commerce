package com.impati.commerce.member;

import com.impati.commerce.member.application.port.in.RegistrationUseCase;
import com.impati.commerce.member.application.port.in.VerificationMailDispatchUseCase;
import org.junit.jupiter.api.BeforeEach;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 가입 → 아웃박스 → 발송 경로를 검증한다 (BL-0004, ADR-0010).
 *
 * <p>member-service만 실제로 띄우고 notification-service 호출만 {@link MockRestServiceServer}로
 * 대체한다. 응용 계층 - HttpNotificationClient - JSON 직렬화까지는 실제 코드가 돈다. 여기가
 * 서비스 경계이므로 클라이언트를 목으로 바꾸면 계약이 어긋나도 통과한다.
 *
 * <p>발송기는 멈춰두고 테스트가 직접 한 주기를 돌린다. 주기에 맡기면 언제 돌았는지 알 수 없어
 * 단정할 시점이 없다. 간격을 늘려도 기동 직후 한 번은 도는데, 그 시점에는 아웃박스가 비어
 * 있어 무해하다.
 *
 * <p>재시도 한도를 2로 낮춘다. 한도 자체의 계산은 {@code VerificationMailTest}가 고정하며,
 * 여기서 볼 것은 설정된 한도가 실제로 전달돼 종단 상태에 도달하는지다.
 */
@SpringBootTest(properties = {
        "member.verification-mail-dispatch-interval=3600000",
        "member.verification-mail-retry-delay=60s",
        "member.verification-mail-max-attempts=2"
})
@RequiresDatabase
class VerificationMailDispatchTest {

    private static final String NOTIFICATION_URL =
            "http://localhost:8109/internal/notifications/email-verifications";
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-25T00:00:00Z");

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
    static class MockServerConfiguration {
        @Bean
        MockServerRestClientCustomizer mockServerRestClientCustomizer() {
            var customizer = new MockServerRestClientCustomizer(UnorderedRequestExpectationManager.class);
            customizer.setBufferContent(true);
            return customizer;
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private RegistrationUseCase registrationUseCase;

    @Autowired
    private VerificationMailDispatchUseCase verificationMailDispatchUseCase;

    @Autowired
    private MockServerRestClientCustomizer customizer;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private MockRestServiceServer server;

    /**
     * 아웃박스를 비우고 시작한다.
     *
     * <p>발송은 특정 회원이 아니라 아웃박스 전체를 훑는다. 앞선 테스트가 남긴 PENDING이 있으면
     * 그것까지 보내려 해서 예상하지 않은 요청이 된다. DB는 이 클래스 전용이므로 지워도 된다.
     */
    @BeforeEach
    void setUp() {
        server = customizer.getServer();
        server.reset();
        jdbc.update("delete from verification_mails");
    }

    /**
     * 아웃박스에 적힌 것이 실제로 나가고, 나간 뒤에는 원문 토큰이 남지 않는다.
     *
     * <p>본문까지 보는 이유는 이것이 서비스 경계이기 때문이다. 필드 이름이 어긋나면 상대는
     * null을 받고 링크 없는 메일을 보내는데, 호출 여부만 보면 통과한다.
     */
    @Test
    void sendsQueuedMailAndDiscardsTokenAfterwards() {
        var member = registrationUseCase.register("dispatch@impati.dev", "Dispatch", "dispatch-pw1");
        var token = tokenOf(member.id());
        server.expect(requestTo(NOTIFICATION_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.memberId").value(member.id()))
                .andExpect(jsonPath("$.email").value("dispatch@impati.dev"))
                .andExpect(jsonPath("$.token").value(token))
                .andRespond(withSuccess());

        var summary = verificationMailDispatchUseCase.dispatchPending();

        server.verify();
        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.sent()).isEqualTo(1);
        var row = mailOf(member.id());
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("token")).isNull();
    }

    /**
     * 실패해도 유실되지 않는다. 이것이 BL-0004가 고친 것이다 — 이전에는 로그 한 줄로 사라졌다.
     *
     * <p>실패한 건이 곧바로 다시 집히지 않는 것도 함께 본다. 점유가 백오프이므로 알림 서비스
     * 장애 중에 밀린 건수만큼 재시도가 증폭되지 않는다.
     */
    @Test
    void keepsMailForRetryWhenNotificationFails() {
        var member = registrationUseCase.register("retry@impati.dev", "Retry", "retry-pw1234");
        server.expect(requestTo(NOTIFICATION_URL)).andRespond(withServerError());

        var summary = verificationMailDispatchUseCase.dispatchPending();

        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.sent()).isZero();
        var row = mailOf(member.id());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isNotNull();
        assertThat(row.get("token")).isNotNull();

        // 간격이 지나기 전에는 다시 집히지 않는다. 서버에 기대를 하나만 걸어두었으므로,
        // 여기서 또 보내면 예상하지 않은 요청이 되어 이 테스트가 깨진다.
        assertThat(verificationMailDispatchUseCase.dispatchPending().claimed()).isZero();
    }

    /**
     * 한도를 넘기면 포기하고 토큰을 버린다. 포기한 것이 FAILED로 남아 세어볼 수 있다.
     *
     * <p>무한 재시도를 하지 않는 이유는 사용자에게 재발송 경로가 있고, 만료된 토큰을 계속
     * 보내려 들 이유가 없기 때문이다.
     */
    @Test
    void givesUpAndRecordsFailureAtAttemptLimit() {
        var member = registrationUseCase.register("giveup@impati.dev", "GiveUp", "giveup-pw12");
        server.expect(times(2), requestTo(NOTIFICATION_URL)).andRespond(withServerError());

        verificationMailDispatchUseCase.dispatchPending();
        clock.advance(RETRY_DELAY.plusSeconds(1));
        verificationMailDispatchUseCase.dispatchPending();

        server.verify();
        var row = mailOf(member.id());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempts")).isEqualTo(2);
        assertThat(row.get("token")).isNull();

        // 종단 상태는 더 집히지 않는다. 집히면 발송이 끝나지 않는다.
        clock.advance(RETRY_DELAY.plusSeconds(1));
        assertThat(verificationMailDispatchUseCase.dispatchPending().claimed()).isZero();
    }

    /** 재발송은 항목을 하나 더 만든다. 이전 항목의 결과와 무관하게 새로 보내야 한다. */
    @Test
    void resendQueuesAnotherMail() {
        var member = registrationUseCase.register("resend@impati.dev", "Resend", "resend-pw12");

        registrationUseCase.resendVerification(member.id());

        assertThat(jdbc.queryForObject(
                "select count(*) from verification_mails where member_id = ? and status = 'PENDING'",
                Integer.class, member.id()))
                .isEqualTo(2);
    }

    private String tokenOf(String memberId) {
        return (String) mailOf(memberId).get("token");
    }

    private Map<String, Object> mailOf(String memberId) {
        return jdbc.queryForMap(
                "select status, attempts, last_error, token from verification_mails where member_id = ?",
                memberId);
    }
}
