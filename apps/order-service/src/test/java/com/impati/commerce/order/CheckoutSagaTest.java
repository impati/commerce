package com.impati.commerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutRequest;
import com.impati.commerce.common.ApiContracts.ErrorResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * checkout saga 하네스 씨앗 테스트.
 *
 * <p>order-service만 실제로 띄우고, 나머지 서비스 호출은 {@link MockRestServiceServer}로 stub한다.
 * 즉 컨트롤러 - OrderService - CommerceClients - JSON 직렬화까지는 실제 코드가 돌고,
 * 네트워크 경계만 대체된다. saga 보상 로직이 깨지면 여기서 잡힌다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:order-saga;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CheckoutSagaTest {
    private static final String MEMBER_URL = "http://localhost:8101";
    private static final String CATALOG_URL = "http://localhost:8102";
    private static final String INVENTORY_URL = "http://localhost:8104";
    private static final String CART_URL = "http://localhost:8105";
    private static final String PAYMENT_URL = "http://localhost:8106";
    private static final String SHIPPING_URL = "http://localhost:8107";
    private static final String NOTIFICATION_URL = "http://localhost:8109";

    private static final String MEMBER_ID = "mem_demo";
    private static final String SKU_ID = "sku_tee_white_m";
    private static final String PRODUCT_ID = "prd_tee";
    private static final String RESERVATION_ID = "rsv_seed";
    private static final int QUANTITY = 2;
    private static final long UNIT_PRICE = 29_000L;

    @TestConfiguration
    static class MockServerConfiguration {
        @Bean
        MockServerRestClientCustomizer mockServerRestClientCustomizer() {
            // 서비스 호출 순서가 아니라 "무엇이 호출됐는지"를 검증하고 싶으므로 순서 무시.
            var customizer = new MockServerRestClientCustomizer(UnorderedRequestExpectationManager.class);
            customizer.setBufferContent(true);
            return customizer;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockServerRestClientCustomizer customizer;

    private MockRestServiceServer server;

    /** 재고 예약 요청 본문에서 뽑아낸 주문 id. 실패 경로에서 주문 상태를 다시 조회하는 데 쓴다. */
    private final AtomicReference<String> reservedOrderId = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        server = customizer.getServer();
        server.reset();
        reservedOrderId.set(null);
    }

    @AfterEach
    void verifyAllStubsWereCalled() {
        server.verify();
    }

    @Test
    void capturedPaymentCommitsReservationAndClearsCart() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        server.expect(times(1), requestTo(PAYMENT_URL + "/payments/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.paymentToken").value("card_test_success"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.amount.amount").value(UNIT_PRICE * QUANTITY))
                .andRespond(withSuccess(json(new PaymentResponse(
                        "pay_seed",
                        "ord_ignored",
                        MEMBER_ID,
                        Money.krw(UNIT_PRICE * QUANTITY),
                        "CARD",
                        "CAPTURED",
                        "txn_seed"
                )), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(INVENTORY_URL + "/reservations/" + RESERVATION_ID + "/commit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        server.expect(times(1), requestTo(SHIPPING_URL + "/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(new ShipmentResponse(
                        "shp_seed",
                        "ord_ignored",
                        MEMBER_ID,
                        address(),
                        "READY",
                        "TRK-SEED-0001"
                )), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CART_URL + "/carts/" + MEMBER_ID + "/clear"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        // OrderPaid, ShipmentCreated
        server.expect(times(2), requestTo(NOTIFICATION_URL + "/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        mockMvc.perform(post("/checkouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutRequest(MEMBER_ID, "card_test_success", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("FULFILLING"))
                .andExpect(jsonPath("$.order.paymentId").value("pay_seed"))
                .andExpect(jsonPath("$.order.shipmentId").value("shp_seed"))
                .andExpect(jsonPath("$.order.inventoryReservationId").value(RESERVATION_ID))
                .andExpect(jsonPath("$.order.total.amount").value(UNIT_PRICE * QUANTITY));
    }

    @Test
    void declinedPaymentCancelsOrderReleasesReservationAndKeepsCart() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        server.expect(times(1), requestTo(PAYMENT_URL + "/payments/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.paymentToken").value("card_test_decline"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("payment_declined", "card was declined"))));
        server.expect(times(1), requestTo(INVENTORY_URL + "/reservations/" + RESERVATION_ID + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        // OrderCancelled
        server.expect(times(1), requestTo(NOTIFICATION_URL + "/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        // 장바구니 clear는 stub하지 않는다. 보상 경로에서 호출되면 예상하지 않은 요청으로 테스트가 깨진다.
        // commit도 같은 이유로 stub하지 않는다.

        mockMvc.perform(post("/checkouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutRequest(MEMBER_ID, "card_test_decline", null))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("payment_declined"));

        mockMvc.perform(get("/orders/{orderId}", reservedOrderId.get()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentId").value(nullValue()))
                .andExpect(jsonPath("$.inventoryReservationId").value(RESERVATION_ID));
    }

    private void stubMemberCartAndCatalog() {
        server.expect(times(1), requestTo(MEMBER_URL + "/members/" + MEMBER_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new MemberResponse(
                        MEMBER_ID,
                        "demo@impati.dev",
                        "Demo Customer",
                        "ACTIVE",
                        List.of(address())
                )), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CART_URL + "/carts/" + MEMBER_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new CartResponse(
                        MEMBER_ID,
                        List.of(new CartLineResponse(SKU_ID, QUANTITY))
                )), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CATALOG_URL + "/skus/" + SKU_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(sku()), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CATALOG_URL + "/products/" + PRODUCT_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new ProductResponse(
                        PRODUCT_ID,
                        "Everyday Cotton Tee",
                        "impati",
                        "TOP",
                        "seed product",
                        "ON_SALE",
                        List.of("seed"),
                        List.of(sku())
                )), MediaType.APPLICATION_JSON));
    }

    /** 예약은 성공시키고, 응답 본문 대신 요청 본문에서 주문 id를 확보한다. */
    private void stubReservation() {
        server.expect(times(1), requestTo(INVENTORY_URL + "/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    var body = objectMapper.readValue(
                            ((MockClientHttpRequest) request).getBodyAsString(),
                            ReserveInventoryRequest.class
                    );
                    reservedOrderId.set(body.orderId());
                    return withSuccess(
                            json(new ReservationResponse(RESERVATION_ID, body.orderId(), "RESERVED", body.lines())),
                            MediaType.APPLICATION_JSON
                    ).createResponse(request);
                });
    }

    private SkuResponse sku() {
        return new SkuResponse(
                SKU_ID,
                PRODUCT_ID,
                "White / M",
                Money.krw(UNIT_PRICE),
                Map.of("color", "white", "size", "M"),
                "ON_SALE"
        );
    }

    private AddressResponse address() {
        return new AddressResponse(
                "addr_demo",
                "home",
                "Demo Customer",
                "010-0000-0000",
                "123 Commerce Road",
                "Seoul",
                "04524",
                true
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize test fixture", exception);
        }
    }
}
