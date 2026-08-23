package com.impati.commerce.gateway;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게이트웨이가 접근 토큰을 스스로 검증하는지 본다.
 *
 * <p>이 클래스의 핵심 단언은 {@link #신원을_확인하면서_member_service를_부르지_않는다}이다. 요청당
 * 조회를 없애는 것이 BL-0003의 목적이고, 그것이 사라졌는지는 "member-service 호출이 없다"로만
 * 확인할 수 있다. {@link org.springframework.test.web.client.MockRestServiceServer}는 등록하지 않은
 * 요청이 나가면 실패하므로, 세션 확인 호출이 되살아나면 여기서 잡힌다.
 *
 * <p>나머지는 검증이 실제로 무엇을 보는지 고정한다. 서명만 보면 다른 발급자의 토큰이 통하고,
 * 만료를 보지 않으면 폐기 지연 상한(PD-0014-R8)이 사라진다.
 */
@SpringBootTest(properties = {
        "gateway.access-token.issuer=impati-member",
        "gateway.access-token.public-key=" + AccessTokenVerificationTest.PUBLIC_KEY
})
@AutoConfigureMockMvc
class AccessTokenVerificationTest {
    /** 이 테스트 전용 키 쌍. 운영 키와 섞이지 않도록 프로퍼티로 덮어쓴다. */
    static final String PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaMi9hQTWkLk4Z56rJeDWxAlVwFJCOSryzCAHD/PWJ9cyM78bZQKml8"
                    + "sfuvfVJ8wfGi3YmDzEs0+d3nrxexBdIw==";
    private static final String PRIVATE_KEY =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgZ5lu+6lyLN1KbDrl9jCnfNhsczlgswOBzONCM0fEs7"
                    + "ehRANCAARoyL2FBNaQuThnnqsl4NbECVXAUkI5KvLMIAcP89Yn1zIzvxtlAqaXyx+699UnzB8aLdiYPMSz"
                    + "T53eevF7EF0j";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockServerRestClientCustomizer restClientCustomizer;

    @BeforeEach
    void resetStubs() {
        restClientCustomizer.getServer().reset();
    }

    /**
     * 인증이 필요한 요청을 처리하면서 member-service에 신원을 묻지 않는다.
     *
     * <p>등록한 stub은 프록시 대상인 {@code /members/me} 하나뿐이다. 게이트웨이가 세션 확인을
     * 위해 member-service를 한 번이라도 더 부르면 등록되지 않은 요청이 되어 실패한다.
     */
    @Test
    void 신원을_확인하면서_member_service를_부르지_않는다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo("http://localhost:8101/members/me"))
                .andExpect(header("X-Member-Id", "mem_local"))
                .andRespond(withSuccess(
                        "{\"id\":\"mem_local\",\"email\":\"local@impati.dev\",\"name\":\"local\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/me").header("Authorization", bearer(token("mem_local", Duration.ofMinutes(5)))))
                .andExpect(status().isOk());

        restClientCustomizer.getServer().verify();
    }

    /** 만료된 토큰은 거부된다. 이것이 없으면 폐기 지연 상한이 성립하지 않는다. */
    @Test
    void 만료된_토큰은_거부된다() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(token("mem_expired", Duration.ofMinutes(-1)))))
                .andExpect(status().isUnauthorized());
    }

    /** 다른 키로 서명된 토큰은 거부된다. 서명 검증이 실제로 도는지 잡는다. */
    @Test
    void 다른_키로_서명된_토큰은_거부된다() throws Exception {
        var foreign = signedWith(otherPrivateKey(), "mem_forged", "impati-member", Duration.ofMinutes(5));

        mockMvc.perform(get("/me").header("Authorization", bearer(foreign)))
                .andExpect(status().isUnauthorized());
    }

    /** 발급자가 다른 토큰은 거부된다. 서명만 보면 다른 용도로 발급된 토큰이 통한다. */
    @Test
    void 발급자가_다른_토큰은_거부된다() throws Exception {
        var foreign = signedWith(privateKey(), "mem_other", "someone-else", Duration.ofMinutes(5));

        mockMvc.perform(get("/me").header("Authorization", bearer(foreign)))
                .andExpect(status().isUnauthorized());
    }

    /** 토큰 자리에 아무 문자열이나 오면 거부된다. 파싱 실패가 500으로 새지 않는다. */
    @Test
    void 형식이_아닌_토큰은_거부된다() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer("not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(String memberId, Duration validFor) {
        return signedWith(privateKey(), memberId, "impati-member", validFor);
    }

    private static String signedWith(ECPrivateKey key, String subject, String issuer, Duration validFor) {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(validFor)))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
        try {
            jwt.sign(new ECDSASigner(key));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        return jwt.serialize();
    }

    private static ECPrivateKey privateKey() {
        try {
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_KEY));
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ECPrivateKey otherPrivateKey() {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            return (ECPrivateKey) generator.generateKeyPair().getPrivate();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @TestConfiguration
    static class StubDownstream {
        @Bean
        MockServerRestClientCustomizer restClientCustomizer() {
            return new MockServerRestClientCustomizer();
        }
    }
}
