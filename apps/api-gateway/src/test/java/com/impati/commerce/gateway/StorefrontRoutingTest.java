package com.impati.commerce.gateway;

import com.impati.commerce.gateway.support.MemberIdentity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StorefrontRoutingTest.Configuration.class)
class StorefrontRoutingTest {
    @TestConfiguration static class Configuration {
        @Bean MockServerRestClientCustomizer mockServerRestClientCustomizer() { return new MockServerRestClientCustomizer(); }
    }
    @MockBean MemberIdentity identity;
    @Autowired MockMvc mvc;
    @Autowired MockServerRestClientCustomizer customizer;
    MockRestServiceServer server;
    @BeforeEach void setUp() {
        server = customizer.getServer(); server.reset();
        when(identity.require("Bearer access")).thenReturn("m");
    }
    @AfterEach void verify() { server.verify(); }
    @Test void routesTheAuthenticatedCartToBffWithoutComposingData() throws Exception {
        server.expect(requestTo("http://localhost:8110/cart")).andExpect(header("X-Member-Id", "m"))
                .andRespond(withSuccess("{\"memberId\":\"m\",\"version\":5,\"lines\":[],\"quote\":null,\"unavailable\":[],\"checkoutAllowed\":false}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/cart").header("Authorization", "Bearer access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(5)).andExpect(jsonPath("$.checkoutAllowed").value(false));
    }
    @Test void forwardsTheOriginalKeyAndQuoteToBffAndPreservesErrors() throws Exception {
        server.expect(requestTo("http://localhost:8110/checkout")).andExpect(header("X-Member-Id", "m"))
                .andExpect(header("Idempotency-Key", "key"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"paymentToken\":\"card\",\"quoteId\":\"quote\"}"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"quote_changed\",\"message\":\"Review\"}"));
        mvc.perform(post("/checkout").header("Authorization", "Bearer access").header("Idempotency-Key", "key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentToken\":\"card\",\"quoteId\":\"quote\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("quote_changed"));
    }
}
