package com.impati.commerce.gateway;

import com.impati.commerce.gateway.adapter.in.scheduler.MemberServiceProbe;
import com.impati.commerce.gateway.adapter.in.web.BrowserSession;
import com.impati.commerce.gateway.support.MemberServiceAvailability;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * member-service 장애 판정과 그에 따른 만료 완화를 본다.
 *
 * <p>완화는 <b>만료 하나에만</b> 걸린다. 서명과 발급자는 판정과 무관하게 그대로 검증되며 그쪽은
 * {@link AccessTokenVerificationTest}가 고정한다.
 *
 * <p>판정을 흔드는 것과 흔들지 않는 것을 함께 잡는다. 폐기된 세션의 갱신 거절로 브레이커가
 * 열리면 로그아웃한 사용자 몇 명이 전체를 완화 모드로 민다 (ADR-0008).
 */
@SpringBootTest(properties = {
        "gateway.access-token.issuer=impati-member",
        "gateway.access-token.public-key=" + OutageToleranceTest.PUBLIC_KEY,
        // 상한은 덮어쓰지 않는다. 덮어쓰면 운영 설정이 바뀌어도 테스트가 통과해
        // PD-0014-R9가 정한 24시간을 아무것도 고정하지 못한다. 임계값은 정책이 아니라
        // 메커니즘 손잡이이므로 테스트를 빠르게 하려고 낮춘다.
        "gateway.member-service.failure-threshold=2",
        // 스케줄러가 배경에서 프로브를 돌리면 판정이 흔들린다. 프로브는 테스트가 직접 부른다.
        "gateway.member-service.probe-interval=3600000"
})
@AutoConfigureMockMvc
class OutageToleranceTest {
    static final String PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaMi9hQTWkLk4Z56rJeDWxAlVwFJCOSryzCAHD/PWJ9cyM78bZQKml8"
                    + "sfuvfVJ8wfGi3YmDzEs0+d3nrxexBdIw==";
    private static final String PRIVATE_KEY =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgZ5lu+6lyLN1KbDrl9jCnfNhsczlgswOBzONCM0fEs7"
                    + "ehRANCAARoyL2FBNaQuThnnqsl4NbECVXAUkI5KvLMIAcP89Yn1zIzvxtlAqaXyx+699UnzB8aLdiYPMSz"
                    + "T53eevF7EF0j";
    private static final String REFRESH = "http://localhost:8101/internal/members/sessions/refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockServerRestClientCustomizer restClientCustomizer;

    @Autowired
    private MemberServiceAvailability availability;

    @Autowired
    private MemberServiceProbe probe;

    @BeforeEach
    void reset() {
        restClientCustomizer.getServer().reset();
        availability.recordReachable();
    }

    /** [PD-0014-R8] 정상 판정에서는 만료된 토큰이 거절된다. 완화가 기본값이 아니라는 것을 잡는다. */
    @Test
    void 정상일_때_만료된_토큰은_거절된다() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(expiredBy(Duration.ofMinutes(1)))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * [PD-0014-R9] 장애로 판정되면 만료된 토큰이 상한 안에서 통과한다.
     *
     * <p>이 테스트가 이 작업의 핵심 단언이다. 갱신 실패가 쌓여 판정이 뒤집히고, 그 뒤에는 같은
     * 토큰이 통과한다.
     */
    @Test
    void 장애로_판정되면_만료된_토큰이_통과한다() throws Exception {
        driveToUnavailable();

        restClientCustomizer.getServer()
                .expect(requestTo("http://localhost:8101/members/me"))
                .andRespond(withSuccess(
                        "{\"id\":\"mem_tolerated\",\"email\":\"t@impati.dev\",\"name\":\"t\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/me").header("Authorization", bearer(expiredBy(Duration.ofHours(3)))))
                .andExpect(status().isOk());
    }

    /**
     * [PD-0014-R9] 장애 중 완화 상한은 24시간이다. 3시간은 통과하고 25시간은 거절된다.
     *
     * <p>설정된 값을 그대로 쓰므로 운영 설정을 줄이면 여기서 깨진다. 위의 통과 테스트와 짝이며,
     * 둘 중 하나만 있으면 상한이 0이거나 무한이어도 통과한다.
     */
    @Test
    void 장애_중이어도_상한을_넘으면_거절된다() throws Exception {
        driveToUnavailable();

        mockMvc.perform(get("/me").header("Authorization", bearer(expiredBy(Duration.ofHours(25)))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 폐기된 세션의 갱신 거절은 판정을 흔들지 않는다.
     *
     * <p>404는 member-service가 멀쩡히 답했다는 뜻이다. 이것을 실패로 세면 로그아웃한 사용자
     * 몇 명이 전체를 완화 모드로 민다.
     */
    @Test
    void 폐기된_세션의_갱신_거절은_장애가_아니다() throws Exception {
        for (var attempt = 0; attempt < 5; attempt++) {
            restClientCustomizer.getServer()
                    .expect(requestTo(REFRESH))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));
            mockMvc.perform(post("/sessions/refresh").cookie(sessionCookie("tok_revoked")))
                    .andExpect(status().isUnauthorized());
            restClientCustomizer.getServer().reset();
        }

        assertThat(availability.isUnavailable()).isFalse();
    }

    /**
     * member-service의 5xx도 503으로 나간다.
     *
     * <p>전송 실패와 같은 처리여야 한다. 둘 다 세션에 대해 아무것도 알아내지 못한 상태이고
     * 브레이커도 같게 취급하므로, 클라이언트에게만 다르게 나가면 재시도 판단이 갈린다.
     */
    @Test
    void member_service의_5xx도_503으로_나간다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(REFRESH))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(post("/sessions/refresh").cookie(sessionCookie("tok_any")))
                .andExpect(status().isServiceUnavailable());
    }

    /** 한도에 못 미치는 실패로는 열리지 않는다. 일시적 지연 한두 번에 완화가 켜지면 안 된다. */
    @Test
    void 한도에_못_미치는_실패로는_열리지_않는다() throws Exception {
        failRefreshOnce();

        assertThat(availability.isUnavailable()).isFalse();
    }

    /** member-service가 다시 응답하면 판정이 풀린다. 프로브가 이 경로를 쓴다. */
    @Test
    void 갱신이_다시_성공하면_판정이_풀린다() throws Exception {
        driveToUnavailable();

        restClientCustomizer.getServer()
                .expect(requestTo(REFRESH))
                .andRespond(withSuccess("{\"accessToken\":\"a\",\"accessTokenExpiresAt\":\"2026-01-01T00:00:00Z\"}",
                        MediaType.APPLICATION_JSON));
        mockMvc.perform(post("/sessions/refresh").cookie(sessionCookie("tok_ok")))
                .andExpect(status().isOk());

        assertThat(availability.isUnavailable()).isFalse();
    }

    /**
     * 프로브가 복구를 감지해 판정을 푼다.
     *
     * <p>이것이 없으면 판정이 영영 풀리지 않는다. 열린 동안에는 게이트웨이가 만료 토큰을
     * 통과시켜 갱신 트래픽 자체가 사라지므로, 갱신 성공만으로는 닫을 기회가 오지 않는다.
     */
    @Test
    void 프로브가_복구를_감지해_판정을_푼다() throws Exception {
        driveToUnavailable();

        // 살아 있는 member-service는 모르는 세션을 404로 거절한다. 그것이 곧 "닿았다"는 신호다.
        restClientCustomizer.getServer()
                .expect(requestTo(REFRESH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        probe.probe();

        assertThat(availability.isUnavailable()).isFalse();
    }

    /**
     * 프로브가 여전히 닿지 못하면 판정이 유지된다.
     *
     * <p>이것이 없으면 프로브가 무조건 닫는 구현도 위 테스트를 통과한다.
     */
    @Test
    void 프로브가_실패하면_판정이_유지된다() throws Exception {
        driveToUnavailable();

        restClientCustomizer.getServer()
                .expect(requestTo(REFRESH))
                .andRespond(request -> {
                    throw new IOException("still down");
                });
        probe.probe();

        assertThat(availability.isUnavailable()).isTrue();
    }

    /** 프로브가 보호 대상과 같은 경로를 부른다. 다른 경로를 프로브하면 판정이 어긋난다. */
    @Test
    void 프로브가_갱신_경로를_부른다() throws Exception {
        driveToUnavailable();

        restClientCustomizer.getServer()
                .expect(requestTo(REFRESH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        probe.probe();

        restClientCustomizer.getServer().verify();
    }

    /** 닫혀 있으면 프로브가 member-service를 부르지 않는다. 정상 상태에 부하를 더하지 않는다. */
    @Test
    void 닫혀_있으면_프로브가_부르지_않는다() {
        probe.probe();

        restClientCustomizer.getServer().verify();
    }

    /** 임계값(2)까지 갱신을 실패시켜 장애 판정을 만든다. */
    private void driveToUnavailable() throws Exception {
        failRefreshOnce();
        failRefreshOnce();
        assertThat(availability.isUnavailable()).isTrue();
    }

    private void failRefreshOnce() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(REFRESH))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });
        mockMvc.perform(post("/sessions/refresh").cookie(sessionCookie("tok_any")))
                .andExpect(status().isServiceUnavailable());
        restClientCustomizer.getServer().reset();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Cookie sessionCookie(String token) {
        return new Cookie(BrowserSession.COOKIE_NAME, token);
    }

    /** 이미 만료된 토큰. 만료된 지 {@code past}만큼 지났다. */
    private static String expiredBy(Duration past) {
        var expiredAt = Instant.now().minus(past);
        var claims = new JWTClaimsSet.Builder()
                .subject("mem_tolerated")
                .issuer("impati-member")
                .issueTime(Date.from(expiredAt.minus(Duration.ofMinutes(5))))
                .expirationTime(Date.from(expiredAt))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
        try {
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_KEY));
            jwt.sign(new ECDSASigner((ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        return jwt.serialize();
    }

    @TestConfiguration
    static class StubDownstream {
        @Bean
        MockServerRestClientCustomizer restClientCustomizer() {
            return new MockServerRestClientCustomizer();
        }
    }
}
