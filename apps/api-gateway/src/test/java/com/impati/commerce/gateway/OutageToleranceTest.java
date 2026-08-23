package com.impati.commerce.gateway;

import com.impati.commerce.gateway.adapter.in.scheduler.MemberServiceProbe;
import com.impati.commerce.gateway.support.MemberServiceAvailability;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
        "gateway.member-service.failure-threshold=2",
        "gateway.member-service.outage-tolerance=PT24H",
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

    /** 정상 판정에서는 만료된 토큰이 거절된다. 완화가 기본값이 아니라는 것을 잡는다. */
    @Test
    void 정상일_때_만료된_토큰은_거절된다() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(expiredBy(Duration.ofMinutes(1)))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 장애로 판정되면 만료된 토큰이 통과한다.
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

    /** 완화에도 상한이 있다. 상한을 넘으면 장애 중이어도 거절된다 (PD-0014-R9). */
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
            mockMvc.perform(post("/sessions/refresh").header("Authorization", "Bearer tok_revoked"))
                    .andExpect(status().isUnauthorized());
            restClientCustomizer.getServer().reset();
        }

        assertThat(availability.isUnavailable()).isFalse();
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
        mockMvc.perform(post("/sessions/refresh").header("Authorization", "Bearer tok_ok"))
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

        restClientCustomizer.getServer()
                .expect(requestTo("http://localhost:8101/actuator/health"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));
        probe.probe();

        assertThat(availability.isUnavailable()).isFalse();
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
        mockMvc.perform(post("/sessions/refresh").header("Authorization", "Bearer tok_any"))
                .andExpect(status().isServiceUnavailable());
        restClientCustomizer.getServer().reset();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
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
