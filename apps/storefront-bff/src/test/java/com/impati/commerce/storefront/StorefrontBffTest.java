package com.impati.commerce.storefront;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StorefrontBffTest.Configuration.class)
class StorefrontBffTest {
    @TestConfiguration
    static class Configuration {
        @Bean
        MockServerRestClientCustomizer mockServerRestClientCustomizer() {
            return new MockServerRestClientCustomizer(UnorderedRequestExpectationManager.class);
        }
    }

    private static final String CART_JSON = """
            {"memberId":"m","version":5,"lines":[{"skuId":"sku","quantity":2}]}
            """;
    private static final String ADD_ITEM_JSON = """
            {"skuId":"sku","quantity":2}
            """;

    @Autowired
    private MockServerRestClientCustomizer customizer;

    @Autowired
    private MockMvc mvc;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = customizer.getServer();
        server.reset();
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    /** [PD-0021-R5] 변경 명령은 후속 견적 호출 없이 성공한 상태를 돌려준다. */
    @Test
    void addingReturnsTheCommittedCartWithoutAQuoteLookup() throws Exception {
        server.expect(once(), requestTo("http://localhost:8105/carts/items"))
                .andExpect(header("X-Member-Id", "m"))
                .andExpect(content().json(ADD_ITEM_JSON))
                .andRespond(withSuccess(CART_JSON, MediaType.APPLICATION_JSON));

        mvc.perform(post("/cart/items")
                        .header("X-Member-Id", "m")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADD_ITEM_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    /** [PD-0021-R4] HTTP 경계의 견적·재고 실패에도 구매 수량은 보존한다. */
    @Test
    void rendersPartialFailureInsteadOfAFakeCartOrPrice() throws Exception {
        server.expect(requestTo("http://localhost:8105/carts"))
                .andExpect(header("X-Member-Id", "m"))
                .andRespond(withSuccess(CART_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8102/internal/skus/sku"))
                .andRespond(withServerError());
        server.expect(requestTo("http://localhost:8104/internal/stock/sku"))
                .andRespond(withServerError());
        server.expect(requestTo("http://localhost:8108/internal/purchase-quotes?cartVersion=5"))
                .andRespond(withServerError());

        mvc.perform(get("/cart").header("X-Member-Id", "m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.quote").doesNotExist())
                .andExpect(jsonPath("$.checkoutAllowed").value(false));
    }

    /** [PD-0021-R6] Order의 신규 접수·처리 중·재접수 결과와 상태를 그대로 표현한다. */
    @ParameterizedTest
    @ValueSource(ints = {200, 201, 202})
    void preservesTheOrderAcceptanceStatusAndBody(int acceptanceStatus) throws Exception {
        var checkoutStatus = acceptanceStatus == 202 ? "PROCESSING" : "SUCCEEDED";
        server.expect(requestTo("http://localhost:8108/checkouts/confirmed"))
                .andExpect(header("X-Member-Id", "m"))
                .andExpect(header("Idempotency-Key", "key"))
                .andExpect(content().json("""
                        {"paymentToken":"card","addressId":"address","quoteId":"quote"}
                        """))
                .andRespond(withStatus(HttpStatus.valueOf(acceptanceStatus))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"order":{"id":"ord_original","checkoutStatus":"%s"},
                                 "payment":null,"shipment":null}
                                """.formatted(checkoutStatus)));

        mvc.perform(post("/checkout")
                        .header("X-Member-Id", "m")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentToken":"card","addressId":"address","quoteId":"quote"}
                                """))
                .andExpect(status().is(acceptanceStatus))
                .andExpect(jsonPath("$.order.id").value("ord_original"))
                .andExpect(jsonPath("$.order.checkoutStatus").value(checkoutStatus));
    }

    @Test
    void preservesQuoteChangedAsAnActionableError() throws Exception {
        server.expect(requestTo("http://localhost:8108/checkouts/confirmed"))
                .andExpect(header("Idempotency-Key", "key"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"quote_changed","message":"internal"}
                                """));

        mvc.perform(post("/checkout")
                        .header("X-Member-Id", "m")
                        .header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentToken":"card","quoteId":"quote"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote_changed"));
    }
    @Test
    void forwardsQuantityAndExpectedVersionUsingTheAuthenticatedMember() throws Exception {
        var request = "{\"quantity\":3,\"expectedVersion\":5}";
        server.expect(requestTo("http://localhost:8105/carts/items/sku"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Member-Id", "m"))
                .andExpect(content().json(request))
                .andRespond(withSuccess("""
                        {"memberId":"m","version":6,"lines":[{"skuId":"sku","quantity":3}]}
                        """, MediaType.APPLICATION_JSON));
        mvc.perform(put("/cart/items/sku").header("X-Member-Id", "m")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.lines[0].quantity").value(3));
    }

    @Test
    void forwardsDeletionAndPreservesTheVersionConflict() throws Exception {
        server.expect(requestTo("http://localhost:8105/carts/items/sku?expectedVersion=5"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Member-Id", "m"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"cart_changed\",\"message\":\"Reload\"}"));
        mvc.perform(delete("/cart/items/sku").header("X-Member-Id", "m").param("expectedVersion", "5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cart_changed"));
    }
}
