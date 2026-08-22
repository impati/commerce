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
import com.impati.commerce.order.application.port.out.OrderRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

/**
 * checkout saga 하네스 씨앗 테스트.
 *
 * <p>order-service만 실제로 띄우고, 나머지 서비스 호출은 {@link MockRestServiceServer}로 stub한다.
 * 즉 컨트롤러 - OrderService - CommerceClients - JSON 직렬화까지는 실제 코드가 돌고,
 * 네트워크 경계만 대체된다. saga 보상 로직이 깨지면 여기서 잡힌다.
 *
 * <p>되돌리지 <b>않아야</b> 하는 경로는 그 호출을 stub하지 않는 방식으로 검증한다. 호출되면
 * 예상하지 않은 요청이 되어 테스트가 깨진다.
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
    private static final String PAYMENT_ID = "pay_seed";
    private static final String SHIPMENT_ID = "shp_seed";
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

    @Autowired
    private OrderRepository orders;

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

    /**
     * [PD-0012-R5][PD-0012-R10][PD-0012-R11][PD-0006-R6] 성공 경로의 호출과 순서를 잡는다.
     *
     * <p>순서의 핵심은 매입이 배송 생성 뒤에 있다는 것이다. 배송이 먼저 만들어지므로 배송
     * 생성 실패는 아직 돈이 움직이지 않은 시점에 드러난다.
     */
    @Test
    void capturedPaymentCommitsReservationAndClearsCart() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        stubAuthorize("card_test_success");
        stubCreateShipment();
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(payment("CAPTURED")), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations/" + RESERVATION_ID + "/commit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        server.expect(times(1), requestTo(CART_URL + "/internal/carts/clear"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        // OrderPaid, ShipmentCreated
        server.expect(times(2), requestTo(NOTIFICATION_URL + "/internal/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        mockMvc.perform(checkout("card_test_success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("FULFILLING"))
                .andExpect(jsonPath("$.order.paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.order.shipmentId").value(SHIPMENT_ID))
                .andExpect(jsonPath("$.order.inventoryReservationId").value(RESERVATION_ID))
                .andExpect(jsonPath("$.order.total.amount").value(UNIT_PRICE * QUANTITY))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));
    }

    /**
     * [PD-0012-R6][PD-0012-R7][PD-0011-R6] 승인이 거절되면 예약만 풀고 끝난다.
     *
     * <p>거절이 시스템 오류와 다른 결과로 전달되는 것도 함께 확인한다 — 402가 도메인 언어로
     * 옮겨지지 않으면 여기서 깨진다. 배송과 매입은 시작되지 않았으므로 되돌릴 것이 없다.
     */
    @Test
    void declinedAuthorizationCancelsOrderReleasesReservationAndKeepsCart() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/authorize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.paymentToken").value("card_test_decline"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("payment_declined", "card was declined"))));
        stubReleaseReservation();
        stubCancelledNotification();
        // 배송 생성·취소, 승인 취소, 매입, 예약 확정, 장바구니 비움은 stub하지 않는다.
        // 호출되면 예상하지 않은 요청으로 테스트가 깨진다.

        mockMvc.perform(checkout("card_test_decline"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("payment_declined"));

        assertOrderCancelledWithoutPayment();
    }

    /**
     * [PD-0012-R5][PD-0012-R6] 배송을 만들 수 없으면 승인을 취소한다.
     *
     * <p>이 시점에는 아직 매입하지 않았으므로 승인 취소로 흔적 없이 정리된다. 이전 순서에서는
     * 이미 매입이 끝난 뒤라 되돌릴 수 없었다.
     */
    @Test
    void failedShipmentCreationCancelsAuthorization() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        stubAuthorize("card_test_success");
        server.expect(times(1), requestTo(SHIPPING_URL + "/internal/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        stubCancelPayment();
        stubReleaseReservation();
        stubCancelledNotification();
        // 매입과 배송 취소는 stub하지 않는다. 배송은 만들어지지 않았으므로 취소할 것이 없다.

        // 어댑터가 프로토콜 오류를 도메인 언어로 옮기므로 409다. 옮기지 않으면 raw 예외가 올라온다.
        mockMvc.perform(checkout("card_test_success"))
                .andExpect(status().isConflict());

        assertOrderCancelledWithoutPayment();
    }

    /**
     * [PD-0012-R6][PD-0013-R5] 매입이 실패하면 배송까지 되돌린다.
     *
     * <p>이 경로가 BL-0034의 핵심이다. 매입 전이므로 되돌릴 수 있고, 되돌리지 않으면 배송만
     * 살아 있는 주문이 남는다. 되돌리는 순서는 만든 순서의 역순이다.
     */
    @Test
    void failedCaptureCancelsShipmentAndAuthorization() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        stubAuthorize("card_test_success");
        stubCreateShipment();
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(times(1), requestTo(SHIPPING_URL + "/internal/shipments/" + SHIPMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(shipment("CANCELLED")), MediaType.APPLICATION_JSON));
        stubCancelPayment();
        stubReleaseReservation();
        stubCancelledNotification();

        mockMvc.perform(checkout("card_test_success"))
                .andExpect(status().isConflict());

        assertOrderCancelledWithoutPayment();
    }

    /**
     * [PD-0012-R8][PD-0012-R9] 매입 이후의 실패는 주문을 되돌리지 않는다.
     *
     * <p>예약 확정이 실패해도 체크아웃은 성립한 것으로 응답한다. 예약된 재고는 이미 가용
     * 수량에서 빠져 있으므로 초과 판매가 생기지 않는다. 되돌리면 팔린 재고가 다시 팔린다.
     */
    @Test
    void failureAfterCaptureDoesNotCancelTheOrder() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        stubAuthorize("card_test_success");
        stubCreateShipment();
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(payment("CAPTURED")), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations/" + RESERVATION_ID + "/commit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(times(1), requestTo(CART_URL + "/internal/carts/clear"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        server.expect(times(2), requestTo(NOTIFICATION_URL + "/internal/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        // 배송 취소, 승인 취소, 예약 해제는 stub하지 않는다. 되돌리면 테스트가 깨진다.

        mockMvc.perform(checkout("card_test_success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("FULFILLING"));

        mockMvc.perform(get("/orders/{orderId}", reservedOrderId.get()).header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLING"))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID));
    }

    /**
     * [PD-0012-R13][PD-0011-R4] 매입 응답을 못 받으면 한 번 더 시도한다.
     *
     * <p>응답 유실은 매입 실패가 아니다. 상대는 처리를 마쳤을 수 있고 매입은 멱등하므로,
     * 다시 부르면 그 결과를 그대로 돌려받는다. 재시도가 곧 확인이다.
     */
    @Test
    void lostCaptureResponseIsRetriedAndSucceeds() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        stubAuthorize("card_test_success");
        stubCreateShipment();
        // 첫 호출은 응답을 만들지 않아 전송 실패가 된다. 둘째 호출이 확정한다.
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("read timed out");
                });
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(payment("CAPTURED")), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations/" + RESERVATION_ID + "/commit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        server.expect(times(1), requestTo(CART_URL + "/internal/carts/clear"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        server.expect(times(2), requestTo(NOTIFICATION_URL + "/internal/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        // 배송 취소·승인 취소·예약 해제는 stub하지 않는다. 되돌리면 테스트가 깨진다.

        mockMvc.perform(checkout("card_test_success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("FULFILLING"));
    }

    /**
     * [PD-0012-R12] 두 번 모두 결과를 못 받으면 되돌리되 결제 미확인으로 표시한다.
     *
     * <p>이 경로가 이번 작업의 마지막 구멍이었다. 모르는 것을 실패로 단정해 되돌리면 매입된
     * 대금이 그대로 남는다. 표시가 없으면 그 주문을 다시 찾을 수 없다.
     */
    @Test
    void repeatedlyLostCaptureResponseMarksTheOrderForRefundCheck() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        stubAuthorize("card_test_success");
        stubCreateShipment();
        server.expect(times(2), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("read timed out");
                });
        server.expect(times(1), requestTo(SHIPPING_URL + "/internal/shipments/" + SHIPMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(shipment("CANCELLED")), MediaType.APPLICATION_JSON));
        // 매입됐을 수 있으므로 취소는 409로 거절될 수 있다. 그것을 삼키고 표시로 남긴다.
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("conflict", "captured payment cannot be cancelled"))));
        stubReleaseReservation();
        stubCancelledNotification();

        mockMvc.perform(checkout("card_test_success"))
                .andExpect(status().isBadGateway());

        assertOrderCancelledWithoutPayment();
        assertThat(unresolvedOrderIds()).contains(reservedOrderId.get());
    }

    /**
     * [PD-0012-R12] 매입 앞 단계의 결과 불명은 표시하지 않는다.
     *
     * <p>대금이 아직 움직이지 않았으므로 환불 대상이 아니다. 원인 코드만 보고 표시하면
     * 여기서 거짓 양성이 나온다.
     */
    @Test
    void unknownOutcomeBeforeCaptureDoesNotMarkTheOrder() throws Exception {
        stubMemberCartAndCatalog();
        stubReservation();
        stubAuthorize("card_test_success");
        server.expect(times(1), requestTo(SHIPPING_URL + "/internal/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("read timed out");
                });
        stubCancelPayment();
        stubReleaseReservation();
        stubCancelledNotification();

        mockMvc.perform(checkout("card_test_success"))
                .andExpect(status().isBadGateway());

        assertThat(unresolvedOrderIds()).doesNotContain(reservedOrderId.get());
    }

    private List<String> unresolvedOrderIds() {
        return orders.findWithUnknownPaymentOutcome().stream().map(order -> order.id()).toList();
    }

    private MockHttpServletRequestBuilder checkout(String paymentToken) {
        return post("/checkouts")
                .header("X-Member-Id", MEMBER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CheckoutRequest(paymentToken, null)));
    }

    private void assertOrderCancelledWithoutPayment() throws Exception {
        mockMvc.perform(get("/orders/{orderId}", reservedOrderId.get()).header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.inventoryReservationId").value(RESERVATION_ID));
    }

    private void stubAuthorize(String paymentToken) {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/authorize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.paymentToken").value(paymentToken))
                .andExpect(MockRestRequestMatchers.jsonPath("$.amount.amount").value(UNIT_PRICE * QUANTITY))
                .andRespond(withSuccess(json(payment("AUTHORIZED")), MediaType.APPLICATION_JSON));
    }

    private void stubCancelPayment() {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(payment("CANCELLED")), MediaType.APPLICATION_JSON));
    }

    private void stubCreateShipment() {
        server.expect(times(1), requestTo(SHIPPING_URL + "/internal/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(shipment("READY")), MediaType.APPLICATION_JSON));
    }

    private void stubReleaseReservation() {
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations/" + RESERVATION_ID + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }

    private void stubCancelledNotification() {
        server.expect(times(1), requestTo(NOTIFICATION_URL + "/internal/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }

    private PaymentResponse payment(String status) {
        return new PaymentResponse(
                PAYMENT_ID,
                "ord_ignored",
                MEMBER_ID,
                Money.krw(UNIT_PRICE * QUANTITY),
                "CARD",
                status
        );
    }

    private ShipmentResponse shipment(String status) {
        return new ShipmentResponse(
                SHIPMENT_ID,
                "ord_ignored",
                MEMBER_ID,
                address(),
                status,
                "TRK-SEED-0001"
        );
    }

    private void stubMemberCartAndCatalog() {
        server.expect(times(1), requestTo(MEMBER_URL + "/internal/members/" + MEMBER_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new MemberResponse(
                        MEMBER_ID,
                        "demo@impati.dev",
                        "Demo Customer",
                        "ACTIVE",
                        List.of(address())
                )), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CART_URL + "/carts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(new CartResponse(
                        MEMBER_ID,
                        List.of(new CartLineResponse(SKU_ID, QUANTITY))
                )), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(CATALOG_URL + "/internal/skus/" + SKU_ID))
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
        server.expect(times(1), requestTo(INVENTORY_URL + "/internal/reservations"))
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
