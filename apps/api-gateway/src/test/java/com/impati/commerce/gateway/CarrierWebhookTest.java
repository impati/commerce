package com.impati.commerce.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "gateway.carrier-webhook.secret=test-secret")
@AutoConfigureMockMvc
@Import(CarrierWebhookTest.Configuration.class)
class CarrierWebhookTest {
    private static final Instant NOW = Instant.parse("2026-09-24T03:00:00Z");

    @TestConfiguration
    static class Configuration {
        @Bean
        MockServerRestClientCustomizer restClientCustomizer() {
            return new GatewayRestClientStubs();
        }
    }

    @MockBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockServerRestClientCustomizer customizer;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = customizer.getServer();
        server.reset();
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    /** [PD-0025-R7] 유효한 서명만 공통 배송 사건으로 바뀌어 Shipping에 전달된다. */
    @Test
    void verifiesAndForwardsNormalizedCarrierEvent() throws Exception {
        var body = """
                {"trackingNumber":"TRK-1","status":"PICKED_UP","occurredAt":"2026-09-24T02:59:00Z"}
                """.strip();
        var timestamp = Long.toString(NOW.getEpochSecond());
        server.expect(requestTo("http://localhost:8107/internal/carrier-events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"eventId":"evt-1","carrierCode":"PRIMARY","trackingNumber":"TRK-1",
                         "type":"PICKED_UP","occurredAt":1790218740}
                        """))
                .andRespond(withSuccess("""
                        {"eventId":"evt-1","shipmentId":"shp-1","result":"APPLIED","shipmentStatus":"IN_TRANSIT"}
                        """, MediaType.APPLICATION_JSON));

        mvc.perform(post("/carrier/webhooks/events")
                        .header("X-Carrier-Event-Id", "evt-1")
                        .header("X-Carrier-Timestamp", timestamp)
                        .header("X-Carrier-Signature", signature("evt-1", timestamp, body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentStatus").value("IN_TRANSIT"));
    }

    @Test
    void rejectsInvalidSignatureWithoutCallingShipping() throws Exception {
        var body = "{\"trackingNumber\":\"TRK-1\",\"status\":\"PICKED_UP\",\"occurredAt\":\"2026-09-24T02:59:00Z\"}";

        mvc.perform(post("/carrier/webhooks/events")
                        .header("X-Carrier-Event-Id", "evt-1")
                        .header("X-Carrier-Timestamp", Long.toString(NOW.getEpochSecond()))
                        .header("X-Carrier-Signature", "sha256=wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_carrier_webhook"));
    }

    @Test
    void rejectsExpiredSignedRequest() throws Exception {
        var body = "{\"trackingNumber\":\"TRK-1\",\"status\":\"PICKED_UP\",\"occurredAt\":\"2026-09-24T02:00:00Z\"}";
        var timestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());

        mvc.perform(post("/carrier/webhooks/events")
                        .header("X-Carrier-Event-Id", "evt-old")
                        .header("X-Carrier-Timestamp", timestamp)
                        .header("X-Carrier-Signature", signature("evt-old", timestamp, body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private static String signature(String eventId, String timestamp, String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var value = eventId + "\n" + timestamp + "\n" + body;
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
