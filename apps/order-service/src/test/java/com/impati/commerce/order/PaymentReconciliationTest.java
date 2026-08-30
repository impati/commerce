package com.impati.commerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.ApiContracts.ErrorResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.order.application.port.in.PaymentReconciliationUseCase;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 결제 미확인으로 표시된 주문의 정리 (PD-0015).
 *
 * <p>order-service만 실제로 띄우고 payment-service 호출만 stub한다. 즉 유스케이스 - 클라이언트 -
 * JSON 직렬화 - 저장소의 점유 SQL까지 실제 코드가 돈다.
 *
 * <p>부르지 <b>않아야</b> 하는 호출은 stub하지 않는 방식으로 검증한다. 호출되면 예상하지 않은
 * 요청이 되어 깨진다.
 *
 * <p>스케줄러 주기를 한 시간으로 밀어 테스트가 유스케이스를 직접 부르게 한다. 주기가 짧으면
 * 정리가 테스트와 겹쳐 stub을 먼저 소비한다.
 */
@SpringBootTest(properties = {
        "orders.payment-reconcile-interval=3600000",
        "orders.payment-reconcile-retry-delay=60s",
        "orders.payment-reconcile-batch-size=50",
        // 사건 발행 릴레이를 끈다 (BL-0049: 끄는 것이 규율에 달려 있다).
        "orders.event-publish-interval=3600000"
})
@RequiresDatabase
class PaymentReconciliationTest {

    private static final String PAYMENT_URL = "http://localhost:8106";
    private static final String MEMBER_ID = "mem_reconcile";
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    /** 점유와 재시도 간격이 전부 시각 판정이므로 시계를 붙잡는다. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-24T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        MockServerRestClientCustomizer mockServerRestClientCustomizer() {
            var customizer = new MockServerRestClientCustomizer(UnorderedRequestExpectationManager.class);
            customizer.setBufferContent(true);
            return customizer;
        }
    }

    @Autowired
    private PaymentReconciliationUseCase paymentReconciliationUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderChanges orderChanges;

    @Autowired
    private MockServerRestClientCustomizer customizer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private MockRestServiceServer server;

    /**
     * 표시된 주문은 테이블 전체가 정리 대상이므로 앞선 테스트가 남긴 주문이 이 테스트의 stub을
     * 소비한다. 컨텍스트와 DB가 테스트 간에 공유되는 것을 전제하고 매번 비운다.
     */
    @BeforeEach
    void setUp() {
        server = customizer.getServer();
        server.reset();
        jdbc.update("delete from order_lines");
        jdbc.update("delete from order_events");
        jdbc.update("delete from orders");
    }

    @AfterEach
    void verifyAllStubsWereCalled() {
        server.verify();
    }

    /** [PD-0015-R2][PD-0015-R4] 매입돼 있으면 환불하고 표시를 해제한다. */
    @Test
    void capturedPaymentIsRefundedAndMarkCleared() {
        var order = markedOrder("pay_captured");
        stubLookup("pay_captured", "CAPTURED");
        stubAction("pay_captured", "refund");

        var summary = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(summary.candidates()).isEqualTo(1);
        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.resolved()).isEqualTo(1);
        assertThat(reloaded(order).paymentOutcomeUnknown()).isFalse();
    }

    /**
     * [PD-0015-R3] 승인만 돼 있으면 환불이 아니라 취소한다.
     *
     * <p>환불은 매입된 결제만 대상이므로 (PD-0011-R8) 여기서 환불을 부르면 결제가 거절한다.
     * 표시된 주문의 결제가 승인으로 남는 것은 체크아웃 롤백의 취소 호출까지 실패한 경우다.
     */
    @Test
    void authorizedPaymentIsCancelledAndMarkCleared() {
        var order = markedOrder("pay_authorized");
        stubLookup("pay_authorized", "AUTHORIZED");
        stubAction("pay_authorized", "cancel");

        paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(reloaded(order).paymentOutcomeUnknown()).isFalse();
    }

    /**
     * [PD-0015-R2][PD-0015-R4] 이미 취소돼 있으면 아무것도 부르지 않고 표시만 해제한다.
     *
     * <p>표시된 주문의 다수가 이 경우다. 롤백이 표시하기 전에 승인 취소를 먼저 시도하므로,
     * 매입 요청이 결제 서비스에 닿지 않았다면 결제는 이미 취소돼 있다. 조회 외의 stub이 없으므로
     * 환불이나 취소를 부르면 예상하지 않은 요청으로 깨진다.
     */
    @Test
    void alreadyCancelledPaymentOnlyClearsMark() {
        var order = markedOrder("pay_cancelled");
        stubLookup("pay_cancelled", "CANCELLED");

        var summary = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(summary.resolved()).isEqualTo(1);
        assertThat(reloaded(order).paymentOutcomeUnknown()).isFalse();
    }

    /** [PD-0015-R4] 이미 환불돼 있으면 다시 환불하지 않는다. 정리가 중복 실행된 경우다. */
    @Test
    void alreadyRefundedPaymentOnlyClearsMark() {
        var order = markedOrder("pay_refunded");
        stubLookup("pay_refunded", "REFUNDED");

        paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(reloaded(order).paymentOutcomeUnknown()).isFalse();
    }

    /**
     * [PD-0015-R6] 결제 상태를 묻지 못하면 표시가 남는다.
     *
     * <p>표시를 해제하면 대금이 나간 주문을 다시 찾을 수 없다. 실패는 상태 변화가 아니라 모르는
     * 상태의 연장이다.
     */
    @Test
    void lookupFailureKeepsMark() {
        var order = markedOrder("pay_unreachable");
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/pay_unreachable"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("internal_error", "payment service is down"))));

        var summary = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.resolved()).isZero();
        assertThat(reloaded(order).paymentOutcomeUnknown()).isTrue();
    }

    /**
     * [PD-0015-R8] 실패한 주문은 최소 간격이 지나기 전에 다시 집히지 않는다.
     *
     * <p>이것이 없으면 결제 서비스 장애 중에 밀린 주문 수만큼 요청이 매 주기 반복되고, 그 부하가
     * 장애를 길게 만든다. 두 번째 호출에 stub이 없는 것이 검증이다 — 다시 집으면 예상하지 않은
     * 요청으로 깨진다.
     */
    @Test
    void failedOrderIsNotRetriedBeforeRetryDelay() {
        var order = markedOrder("pay_backoff");
        stubUnavailableLookup("pay_backoff");

        paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();
        var tooSoon = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(tooSoon.candidates()).isZero();
        assertThat(reloaded(order).paymentOutcomeUnknown()).isTrue();
    }

    /**
     * [PD-0015-R6][PD-0015-R8] 간격이 지나면 다시 집히고, 그 시도로 정리가 끝난다.
     *
     * <p>stub을 먼저 다 걸어둔다 — 요청이 시작된 뒤에는 추가할 수 없다. 조회는 등록 순서대로
     * 소비되므로 첫 시도가 503을 받고 두 번째 시도가 매입 상태를 받는다.
     */
    @Test
    void failedOrderIsRetriedAfterRetryDelay() {
        var order = markedOrder("pay_retry");
        stubUnavailableLookup("pay_retry");
        stubLookup("pay_retry", "CAPTURED");
        stubAction("pay_retry", "refund");

        var failed = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();
        assertThat(failed.resolved()).isZero();

        clock.advance(RETRY_DELAY.plusSeconds(1));
        var retried = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(retried.claimed()).isEqualTo(1);
        assertThat(retried.resolved()).isEqualTo(1);
        assertThat(reloaded(order).paymentOutcomeUnknown()).isFalse();
    }

    /**
     * [PD-0015-R5] 정리는 주문 상태를 바꾸지 않는다.
     *
     * <p>환불로 주문을 되살리면 재고와 배송이 없는 주문이 성립한다. 취소로 끝난 것은 그대로 둔다.
     */
    @Test
    void reconciliationLeavesOrderCancelled() {
        var order = markedOrder("pay_status");
        stubLookup("pay_status", "CAPTURED");
        stubAction("pay_status", "refund");

        paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(reloaded(order).status()).isEqualTo("CANCELLED");
    }

    /** [PD-0015-R1] 표시되지 않은 주문은 대상이 아니다. */
    @Test
    void unmarkedOrderIsNotACandidate() {
        var order = newOrder();
        order.attachPayment("pay_untouched");
        orderChanges.commit(order);

        var summary = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(summary.candidates()).isZero();
    }

    /** 여러 건이 밀려 있어도 한 주기에 모두 처리된다. 배치 상한 안이면 남기지 않는다. */
    @Test
    void reconcilesEveryDueOrderInOneRun() {
        var first = markedOrder("pay_batch_1");
        var second = markedOrder("pay_batch_2");
        stubLookup("pay_batch_1", "CANCELLED");
        stubLookup("pay_batch_2", "CAPTURED");
        stubAction("pay_batch_2", "refund");

        var summary = paymentReconciliationUseCase.reconcileUnknownPaymentOutcomes();

        assertThat(summary.resolved()).isEqualTo(2);
        assertThat(reloaded(first).paymentOutcomeUnknown()).isFalse();
        assertThat(reloaded(second).paymentOutcomeUnknown()).isFalse();
    }

    private void stubLookup(String paymentId, String status) {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + paymentId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(payment(paymentId, status)), MediaType.APPLICATION_JSON));
    }

    private void stubUnavailableLookup(String paymentId) {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + paymentId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(new ErrorResponse("service_unavailable", "payment service is down"))));
    }

    private void stubAction(String paymentId, String action) {
        server.expect(times(1), requestTo(PAYMENT_URL + "/internal/payments/" + paymentId + "/" + action))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        json(payment(paymentId, action.equals("refund") ? "REFUNDED" : "CANCELLED")),
                        MediaType.APPLICATION_JSON));
    }

    private PaymentResponse payment(String paymentId, String status) {
        return new PaymentResponse(paymentId, "ord_ignored", MEMBER_ID, Money.krw(29_000), "CARD", status);
    }

    /** 매입 결과를 확인하지 못한 채 취소된 주문. 체크아웃 롤백이 남기는 상태다 (PD-0012-R12). */
    private Order markedOrder(String paymentId) {
        var order = newOrder();
        order.attachPayment(paymentId);
        order.cancel("test");
        order.markPaymentOutcomeUnknown();
        orderChanges.commit(order);
        return order;
    }

    private Order newOrder() {
        return new Order(
                MEMBER_ID,
                List.of(new OrderLine("sku_tee_white_m", "prd_tee", "Tee", "White M", 1, Money.krw(29_000))),
                new Address("adr_1", "home", "Demo Customer", "010", "1 Main", "Seoul", "04524", true)
        );
    }

    private Order reloaded(Order order) {
        return orderRepository.findById(order.id()).orElseThrow();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
