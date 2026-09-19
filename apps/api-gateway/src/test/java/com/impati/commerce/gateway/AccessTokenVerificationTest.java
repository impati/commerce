package com.impati.commerce.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
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
import java.util.List;
import org.springframework.http.HttpMethod;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

    /** [PD-0022-R1] 위조한 신원 헤더만으로 배송지 관리 API를 호출할 수 없다. */
    @Test
    void addressManagementRequiresAuthentication() throws Exception {
        for (var request : List.of(post("/me/addresses").content("{}"),
                put("/me/addresses/a").content("{}"),
                put("/me/addresses/a/default").content("{}"),
                delete("/me/addresses/a").param("expectedVersion", "0"))) {
            mockMvc.perform(request.header("X-Member-Id", "forged").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
        restClientCustomizer.getServer().verify();
    }

    /** [PD-0022-R1, PD-0022-R4] 새 주소 관리 경로도 서명 토큰의 신원과 예상 버전만 전달한다. */
    @Test
    void forwardsAddressManagementWithAuthenticatedIdentityAndVersion() throws Exception {
        var authorization = bearer(token("mem_local", Duration.ofMinutes(5)));
        var addressBody = "{\"alias\":\"home\",\"recipient\":\"Owner\",\"phone\":\"010\",\"line1\":\"Road\","
                + "\"city\":\"Seoul\",\"postalCode\":\"00000\",\"expectedVersion\":4}";
        var server = restClientCustomizer.getServer();
        server.expect(requestTo("http://localhost:8101/members/me/addresses/a"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Member-Id", "mem_local"))
                .andExpect(content().json(addressBody))
                .andRespond(withSuccess("{\"id\":\"mem_local\",\"addresses\":[],\"addressBookVersion\":5}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8101/members/me/addresses/a/default"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Member-Id", "mem_local"))
                .andExpect(content().json("{\"expectedVersion\":5}"))
                .andRespond(withSuccess("{\"id\":\"mem_local\",\"addresses\":[],\"addressBookVersion\":6}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8101/members/me/addresses/a?expectedVersion=6"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Member-Id", "mem_local"))
                .andRespond(withSuccess("{\"id\":\"mem_local\",\"addresses\":[],\"addressBookVersion\":7}", MediaType.APPLICATION_JSON));
        mockMvc.perform(put("/me/addresses/a").header("Authorization", authorization).header("X-Member-Id", "forged")
                .contentType(MediaType.APPLICATION_JSON).content(addressBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.addressBookVersion").value(5));
        mockMvc.perform(put("/me/addresses/a/default").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":5}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/me/addresses/a").header("Authorization", authorization).param("expectedVersion", "6"))
                .andExpect(status().isOk());
        server.verify();
    }

    /** [PD-0020-R9] 주문 URL이나 위조한 신원 헤더만으로는 주문 내역을 조회하지 못한다. */
    @Test
    void orderHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/orders").header("X-Member-Id", "mem_forged"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/orders/ord_known").header("X-Member-Id", "mem_forged"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/orders/ord_known/cancellation").header("X-Member-Id", "mem_forged"))
                .andExpect(status().isUnauthorized());
        restClientCustomizer.getServer().verify();
    }

    /** [PD-0024-R1][PD-0024-R10] 취소 요청도 토큰 소유자의 신원만 하위 서비스에 전달한다. */
    @Test
    void forwardsOrderCancellationWithAuthenticatedMemberOnly() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo("http://localhost:8108/orders/ord_owned/cancellation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Member-Id", "mem_local"))
                .andRespond(withSuccess("{\"orderId\":\"ord_owned\",\"status\":\"PROCESSING\"}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/orders/ord_owned/cancellation")
                        .header("Authorization", bearer(token("mem_local", Duration.ofMinutes(5))))
                        .header("X-Member-Id", "mem_forged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        restClientCustomizer.getServer().verify();
    }

    /** [PD-0020-R9] 페이지 위치는 전달하지만 회원은 서명 토큰의 소유자로만 전달한다. */
    @Test
    void forwardsOrderCursorWithAuthenticatedMemberOnly() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo("http://localhost:8108/orders?cursor=opaque_cursor&size=2"))
                .andExpect(header("X-Member-Id", "mem_local"))
                .andRespond(withSuccess("{\"items\":[],\"nextCursor\":null}", MediaType.APPLICATION_JSON));
        mockMvc.perform(get("/orders").param("cursor", "opaque_cursor").param("size", "2")
                        .param("memberId", "mem_forged").header("X-Member-Id", "mem_forged")
                        .header("Authorization", bearer(token("mem_local", Duration.ofMinutes(5)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        restClientCustomizer.getServer().verify();
    }

    @Test
    void forwardsCustomerDetailIncludingTimeline() throws Exception {
        restClientCustomizer.getServer().expect(requestTo("http://localhost:8108/orders/ord_owned"))
                .andExpect(header("X-Member-Id", "mem_local"))
                .andRespond(withSuccess("""
                        {"id":"ord_owned","orderedAt":"2026-09-16T03:00:00Z","checkoutResult":"CHECKING",
                         "orderStatus":null,"lines":[],"total":{"amount":10000,"currency":"KRW"},
                         "shippingAddress":{"recipient":"Owner","phone":"010","line1":"Road","city":"Seoul","postalCode":"12345"},
                         "trackingNumber":null,"timeline":[{"type":"ORDER_CREATED","occurredAt":"2026-09-16T03:00:00Z"}]}
                        """, MediaType.APPLICATION_JSON));
        mockMvc.perform(get("/orders/ord_owned")
                        .header("Authorization", bearer(token("mem_local", Duration.ofMinutes(5)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkoutResult").value("CHECKING"))
                .andExpect(jsonPath("$.timeline[0].occurredAt").value("2026-09-16T03:00:00Z"));
        restClientCustomizer.getServer().verify();
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

    /**
     * 다른 알고리즘으로 서명된 토큰은 거부된다.
     *
     * <p>알고리즘 혼동(alg confusion)이다. 검증자가 토큰이 선언한 알고리즘을 그대로 믿으면,
     * 공개키를 HMAC 비밀키로 삼아 서명한 토큰이 통과한다 — 공개키는 누구나 알 수 있으므로
     * 임의 신원을 위조할 수 있다. ADR-0007이 서명을 직접 만들지 않기로 한 이유가 이것이며,
     * 그 방어가 실제로 서 있는지는 문서가 아니라 이 테스트가 답한다.
     */
    @Test
    void 다른_알고리즘으로_서명된_토큰은_거부된다() throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .subject("mem_confused")
                .issuer("impati-member")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        // 공개키를 HMAC 비밀키로 쓴다. 공격자가 실제로 시도하는 모양이다.
        jwt.sign(new MACSigner(Base64.getDecoder().decode(PUBLIC_KEY)));

        mockMvc.perform(get("/me").header("Authorization", bearer(jwt.serialize())))
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
            return new GatewayRestClientStubs();
        }
    }
}
