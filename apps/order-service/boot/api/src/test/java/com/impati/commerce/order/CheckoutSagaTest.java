package com.impati.commerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutCartRequest;
import com.impati.commerce.common.ApiContracts.CheckoutCartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutRequest;
import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ErrorResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.order.application.port.in.CheckoutRecoveryUseCase;
import com.impati.commerce.test.RequiresDatabase;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API가 시작한 체크아웃을 같은 진행 기록으로 워커가 이어받는지 검증한다 (ADR-0018). */
@SpringBootTest(properties = "orders.event-publish-interval=3600000")
@RequiresDatabase
@AutoConfigureMockMvc
class CheckoutSagaTest {
    private static final String MEMBER_URL = "http://localhost:8101";
    private static final String CATALOG_URL = "http://localhost:8102";
    private static final String INVENTORY_URL = "http://localhost:8104";
    private static final String CART_URL = "http://localhost:8105";
    private static final String PAYMENT_URL = "http://localhost:8106";
    private static final String SHIPPING_URL = "http://localhost:8107";
    private static final String MEMBER_ID = "mem_demo";
    private static final String SKU_ID = "sku_tee_white_m";
    private static final String PRODUCT_ID = "prd_tee";
    private static final String RESERVATION_ID = "rsv_seed";
    private static final String PAYMENT_ID = "pay_seed";
    private static final String SHIPMENT_ID = "shp_seed";
    private static final int QUANTITY = 2;
    private static final long UNIT_PRICE = 29_000L;
    private static final long CART_VERSION = 7L;

    @TestConfiguration
    static class MockServerConfiguration {
        @Bean
        MockServerRestClientCustomizer mockServerRestClientCustomizer() {
            var customizer = new MockServerRestClientCustomizer(UnorderedRequestExpectationManager.class);
            customizer.setBufferContent(true);
            return customizer;
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockServerRestClientCustomizer customizer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CheckoutRecoveryUseCase recovery;

    private MockRestServiceServer server;
    private final AtomicReference<String> orderId = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        server = customizer.getServer();
        server.reset();
        orderId.set(null);
        jdbc.update("delete from checkout_progress");
        jdbc.update("delete from order_events");
        jdbc.update("delete from order_lines");
        jdbc.update("delete from orders");
    }

    @AfterEach
    void verifyAllRequests() {
        server.verify();
    }

    @Test
    void completesOnceAndReturnsTheSameOrderForTheSameKey() throws Exception {
        stubCheckoutInputs();
        stubCartDetach();
        stubReservation();
        stubAuthorization("card_success");
        stubShipmentCreation();
        stubCapture();
        stubCommitReservation();
        stubSuccessDecoration(3);

        var first = mockMvc.perform(checkout("checkout-key", "card_success"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.checkoutStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.order.status").value("FULFILLING"))
                .andExpect(jsonPath("$.order.paymentId").value(PAYMENT_ID))
                .andReturn();
        var acceptedOrderId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("order").path("id").asText();

        mockMvc.perform(checkout("checkout-key", "card_success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(acceptedOrderId))
                .andExpect(jsonPath("$.order.checkoutStatus").value("SUCCEEDED"));

        mockMvc.perform(get("/orders/{orderId}/checkout-result", acceptedOrderId)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(acceptedOrderId))
                .andExpect(jsonPath("$.shipment.id").value(SHIPMENT_ID));

        mockMvc.perform(get("/orders/{orderId}/checkout-result", acceptedOrderId)
                        .header("X-Member-Id", "mem_other"))
                .andExpect(status().isNotFound());

        mockMvc.perform(checkout("checkout-key", "another_card"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        assertThat(eventTypes(acceptedOrderId))
                .containsExactly("ORDER_CREATED", "ORDER_PAID", "SHIPMENT_CREATED");
        assertThat(jdbc.queryForObject(
                "select payment_token from checkout_progress where order_id = ?",
                String.class,
                acceptedOrderId
        )).isNull();
    }

    @Test
    void rejectsCheckoutWithoutAnIdempotencyKey() throws Exception {
        mockMvc.perform(post("/checkouts")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutRequest("card_success", "addr_demo"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsADeclineAsFailedAfterReleasingCreatedResources() throws Exception {
        stubCheckoutInputs();
        stubCartDetach();
        stubReservation();
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/authorize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("payment_declined", "declined"))));
        stubMissingShipmentForOrder();
        stubMissingPaymentForOrder();
        stubReservationLookup("RESERVED");
        stubReleaseReservation();

        mockMvc.perform(checkout("declined-key", "card_declined"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.checkoutStatus").value("FAILED"))
                .andExpect(jsonPath("$.order.failureCode").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.order.paymentCleanupStatus").value("DONE"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    @Test
    void workerResumesCompensationAfterATransientLookupFailure() throws Exception {
        stubCheckoutInputs();
        stubCartDetach();
        stubReservation();
        stubAuthorization("card_success");
        server.expect(times(2), requestTo(SHIPPING_URL + "/internal/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(times(2), request -> assertThat(request.getURI().toString())
                        .isEqualTo(SHIPPING_URL + "/internal/shipments/orders/" + orderId.get()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        stubPaymentLookup("AUTHORIZED");
        stubCancelPayment();
        stubReservationLookup("RESERVED");
        stubReleaseReservation();
        stubMissingShipmentForOrder();
        stubPaymentLookup("CANCELLED");
        stubReservationLookup("RELEASED");

        mockMvc.perform(checkout("recover-key", "card_success"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.checkoutStatus").value("FAILED"))
                .andExpect(jsonPath("$.order.paymentCleanupStatus").value("CANCELLING"));

        releaseForRecovery(orderId.get());

        assertThat(recovery.recover(10)).isEqualTo(1);
        mockMvc.perform(get("/orders/{orderId}/checkout-result", orderId.get())
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.checkoutStatus").value("FAILED"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    @Test
    void unknownCaptureOutcomeIsRetriedThenVerifiedAndRefunded() throws Exception {
        stubCheckoutInputs();
        stubCartDetach();
        stubReservation();
        stubAuthorization("card_success");
        stubShipmentCreation();
        server.expect(times(2), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> { throw new SocketTimeoutException("read timed out"); });
        stubShipmentLookup("READY");
        server.expect(times(1), requestTo(SHIPPING_URL + "/internal/shipments/" + SHIPMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(shipment("CANCELLED")), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(payment("CAPTURED")), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(payment("REFUNDED")), MediaType.APPLICATION_JSON));
        stubReservationLookup("RESERVED");
        stubReleaseReservation();

        mockMvc.perform(checkout("unknown-capture-key", "card_success"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.checkoutStatus").value("FAILED"))
                .andExpect(jsonPath("$.order.failureCode").value("CHECKOUT_FAILED"))
                .andExpect(jsonPath("$.order.paymentCleanupStatus").value("DONE"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    private MockHttpServletRequestBuilder checkout(String key, String token) {
        return post("/checkouts")
                .header("X-Member-Id", MEMBER_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CheckoutRequest(token, "addr_demo")));
    }

    private void stubCheckoutInputs() {
        server.expect(times(1), requestTo(MEMBER_URL + "/internal/members/" + MEMBER_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new MemberResponse(
                        MEMBER_ID, "demo@impati.dev", "Demo Customer", "ACTIVE", List.of(address()))),
                        MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CART_URL + "/carts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new CartResponse(
                        MEMBER_ID, List.of(new CartLineResponse(SKU_ID, QUANTITY)), CART_VERSION)),
                        MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CATALOG_URL + "/internal/skus/" + SKU_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(sku()), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CATALOG_URL + "/products/" + PRODUCT_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new ProductResponse(
                        PRODUCT_ID, "Everyday Cotton Tee", "impati", "TOP", "seed product",
                        "ON_SALE", List.of("seed"), List.of(sku()))), MediaType.APPLICATION_JSON));
    }

    private void stubCartDetach() {
        server.expect(times(1), requestTo(CART_URL + "/internal/carts/checkout"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    var body = readBody(request, CheckoutCartRequest.class);
                    assertThat(body.expectedVersion()).isEqualTo(CART_VERSION);
                    orderId.set(body.orderId());
                    return withSuccess(json(new CheckoutCartResponse(
                            body.orderId(), MEMBER_ID, List.of(new CartLineResponse(SKU_ID, QUANTITY)))),
                            MediaType.APPLICATION_JSON).createResponse(request);
                });
    }

    private void stubReservation() {
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    var body = readBody(request, ReserveInventoryRequest.class);
                    assertThat(body.orderId()).isEqualTo(orderId.get());
                    return withSuccess(json(new ReservationResponse(
                            RESERVATION_ID, body.orderId(), "RESERVED", body.lines())),
                            MediaType.APPLICATION_JSON).createResponse(request);
                });
    }

    private void stubAuthorization(String token) {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/authorize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    var body = readBody(request, AuthorizePaymentRequest.class);
                    assertThat(body.orderId()).isEqualTo(orderId.get());
                    assertThat(body.paymentToken()).isEqualTo(token);
                    return withSuccess(json(payment("AUTHORIZED")), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
    }

    private void stubShipmentCreation() {
        server.expect(times(1), requestTo(SHIPPING_URL + "/internal/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    var body = readBody(request, CreateShipmentRequest.class);
                    assertThat(body.orderId()).isEqualTo(orderId.get());
                    return withSuccess(json(shipment("READY")), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
    }

    private void stubCapture() {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(payment("CAPTURED")), MediaType.APPLICATION_JSON));
    }

    private void stubCommitReservation() {
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations/" + RESERVATION_ID + "/commit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }

    private void stubSuccessDecoration(int count) {
        server.expect(times(count), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(payment("CAPTURED")), MediaType.APPLICATION_JSON));
        server.expect(times(count), request -> assertThat(request.getURI().toString())
                        .isEqualTo(SHIPPING_URL + "/internal/shipments/orders/" + orderId.get()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(shipment("READY")), MediaType.APPLICATION_JSON));
    }

    private void stubMissingShipmentForOrder() {
        server.expect(times(1), request -> assertThat(request.getURI().toString())
                        .isEqualTo(SHIPPING_URL + "/internal/shipments/orders/" + orderId.get()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("not_found", "shipment not found"))));
    }

    private void stubMissingPaymentForOrder() {
        server.expect(times(1), request -> assertThat(request.getURI().toString())
                        .isEqualTo(PAYMENT_URL + "/internal/payments/orders/" + orderId.get()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("not_found", "payment not found"))));
    }

    private void stubPaymentLookup(String paymentStatus) {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(payment(paymentStatus)), MediaType.APPLICATION_JSON));
    }

    private void stubCancelPayment() {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(payment("CANCELLED")), MediaType.APPLICATION_JSON));
    }

    private void stubShipmentLookup(String shipmentStatus) {
        server.expect(times(1), request -> assertThat(request.getURI().toString())
                        .isEqualTo(SHIPPING_URL + "/internal/shipments/orders/" + orderId.get()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(shipment(shipmentStatus)), MediaType.APPLICATION_JSON));
    }

    private void stubReservationLookup(String reservationStatus) {
        server.expect(times(1), request -> assertThat(request.getURI().toString())
                        .isEqualTo(INVENTORY_URL + "/internal/reservations/orders/" + orderId.get()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new ReservationResponse(
                        RESERVATION_ID, orderId.get(), reservationStatus,
                        List.of(new ReservationLine(SKU_ID, QUANTITY)))), MediaType.APPLICATION_JSON));
    }

    private void stubReleaseReservation() {
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations/" + RESERVATION_ID + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }

    private void releaseForRecovery(String id) {
        jdbc.update("""
                update checkout_progress
                   set next_attempt_at = date_sub(now(6), interval 1 second),
                       lease_until = date_sub(now(6), interval 1 second)
                 where order_id = ?
                """, id);
    }

    private List<String> eventTypes(String id) {
        return jdbc.queryForList("select type from order_events where order_id = ? order by seq", String.class, id);
    }

    private PaymentResponse payment(String paymentStatus) {
        return new PaymentResponse(PAYMENT_ID, orderId.get(), MEMBER_ID,
                Money.krw(UNIT_PRICE * QUANTITY), "CARD", paymentStatus);
    }

    private ShipmentResponse shipment(String shipmentStatus) {
        return new ShipmentResponse(
                SHIPMENT_ID, orderId.get(), MEMBER_ID, address(), shipmentStatus, "TRK-SEED-0001");
    }

    private SkuResponse sku() {
        return new SkuResponse(SKU_ID, PRODUCT_ID, "White / M", Money.krw(UNIT_PRICE),
                Map.of("color", "white", "size", "M"), "ON_SALE");
    }

    private AddressResponse address() {
        return new AddressResponse("addr_demo", "home", "Demo Customer", "010-0000-0000",
                "123 Commerce Road", "Seoul", "04524", true);
    }

    private <T> T readBody(org.springframework.http.HttpRequest request, Class<T> type) {
        try {
            return objectMapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), type);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to read request body", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize fixture", exception);
        }
    }
}
