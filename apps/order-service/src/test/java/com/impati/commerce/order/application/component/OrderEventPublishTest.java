package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.order.application.port.in.OrderEventPublishUseCase;
import com.impati.commerce.order.application.port.out.NotificationClient;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.BeforeEach;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.TestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 사건이 발행되고 실패가 상태로 남는지 확인한다 (ADR-0012).
 *
 * <p>이 작업의 출발점은 실패가 어댑터에서 사라져 아무도 세지 못했다는 것이다. 여기서 잡는 것이
 * 그 성질 — 실패가 응용 계층에 도달하고, 재시도되고, 한도를 넘기면 상태로 남는다.
 *
 * <p>릴레이가 배경에서 사건을 집어가면 결과가 흔들리므로 주기를 아주 길게 둔다. 정리기도 같다.
 */
@SpringBootTest(properties = {
        "orders.event-publish-interval=3600000",
        "orders.payment-reconcile-interval=3600000",
        "orders.event-publish-max-attempts=3"
})
@RequiresDatabase
class OrderEventPublishTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry, "order-event-publish");
    }
    @Autowired
    private OrderEventPublishUseCase orderEventPublishUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderChanges orderChanges;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private NotificationClient notificationClient;

    /** 사건 테이블이 테스트 간에 공유된다. 앞선 테스트가 남긴 대기 사건이 이 주기에 딸려온다. */
    @BeforeEach
    void setUp() {
        reset(notificationClient);
        jdbc.update("delete from order_events");
        jdbc.update("delete from order_lines");
        jdbc.update("delete from orders");
    }

    @Test
    void publishesPendingEventsAndSettlesThem() {
        var order = savedOrder();
        order.attachPayment("pay_pub");
        order.markPaid();
        orderChanges.commit(order);

        var settled = orderEventPublishUseCase.publishPending();

        assertThat(settled).isEqualTo(2);
        assertThat(statusOf(order.id(), "ORDER_CREATED")).isEqualTo("PUBLISHED");
        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("PUBLISHED");
    }

    /** 멱등 키는 사건 행 id다. 재시도가 같은 사건이므로 키가 그대로다. */
    @Test
    void sendsTheEventIdAsIdempotencyKey() {
        var order = savedOrder();
        order.attachPayment("pay_key");
        order.markPaid();
        orderChanges.commit(order);

        orderEventPublishUseCase.publishPending();

        var captor = ArgumentCaptor.forClass(NotificationEventRequest.class);
        verify(notificationClient).notify(captor.capture());
        var request = captor.getValue();
        assertThat(request.eventType()).isEqualTo("OrderPaid");
        assertThat(request.idempotencyKey()).isEqualTo(eventIdOf(order.id(), "ORDER_PAID"));
    }

    /**
     * 이 구독자가 관심 없는 사건은 알리지 않지만 발행은 성공이다.
     *
     * <p>사건을 남기는 기준은 소비자가 아니라 상태 전이이므로, 소비자 없는 사건이 있는 것은
     * 정상이다. 그것이 아웃박스에 영원히 남으면 안 된다.
     */
    @Test
    void settlesEventsThatThisSubscriberIgnores() {
        var order = savedOrder();

        var settled = orderEventPublishUseCase.publishPending();

        assertThat(settled).isEqualTo(1);
        assertThat(statusOf(order.id(), "ORDER_CREATED")).isEqualTo("PUBLISHED");
        verify(notificationClient, never()).notify(any());
    }

    /**
     * 실패가 상태로 남고 한도를 넘기면 FAILED로 끝난다.
     *
     * <p>예전에는 이 실패가 어댑터에서 사라져 건수조차 셀 수 없었다.
     */
    @Test
    void recordsFailureAndGivesUpAtTheLimit() {
        doThrow(new IllegalStateException("notification down")).when(notificationClient).notify(any());
        var order = savedOrder();
        order.attachPayment("pay_fail");
        order.markPaid();
        orderChanges.commit(order);

        // 첫 주기가 두 사건을 집는다. ORDER_CREATED는 알림이 없어 곧바로 종단되고,
        // ORDER_PAID만 실패로 남는다.
        orderEventPublishUseCase.publishPending();
        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("PENDING");
        assertThat(attemptsOf(order.id(), "ORDER_PAID")).isEqualTo(1);
        assertThat(lastErrorOf(order.id(), "ORDER_PAID")).contains("notification down");

        // 점유가 시각을 밀어뒀으므로 그냥 다시 부르면 집히지 않는다. 시각을 되돌려 다음 주기를
        // 흉내 낸다 — 백오프가 실재한다는 것도 함께 확인된다.
        assertThat(orderEventPublishUseCase.publishPending()).isZero();
        assertThat(attemptsOf(order.id(), "ORDER_PAID")).isEqualTo(1);

        releaseBackoff();
        orderEventPublishUseCase.publishPending();
        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("PENDING");
        assertThat(attemptsOf(order.id(), "ORDER_PAID")).isEqualTo(2);

        releaseBackoff();
        orderEventPublishUseCase.publishPending();
        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("FAILED");
        assertThat(attemptsOf(order.id(), "ORDER_PAID")).isEqualTo(3);

        // 종단된 사건은 다시 집히지 않는다.
        releaseBackoff();
        assertThat(orderEventPublishUseCase.publishPending()).isZero();
        assertThat(attemptsOf(order.id(), "ORDER_PAID")).isEqualTo(3);
    }

    /** 한 건의 실패가 다음 건을 막지 않는다. */
    @Test
    void oneFailureDoesNotBlockTheRest() {
        var order = savedOrder();
        order.attachPayment("pay_iso");
        order.markPaid();
        orderChanges.commit(order);
        order.attachShipment("shp_iso", "TRK-ISO");
        orderChanges.commit(order);

        doThrow(new IllegalStateException("only paid fails"))
                .when(notificationClient)
                .notify(org.mockito.ArgumentMatchers.argThat(
                        request -> request != null && "OrderPaid".equals(request.eventType())));

        orderEventPublishUseCase.publishPending();

        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("PENDING");
        assertThat(statusOf(order.id(), "SHIPMENT_CREATED")).isEqualTo("PUBLISHED");
        verify(notificationClient, times(2)).notify(any());
    }

    /** 취소 사유가 알림 본문까지 전달된다. */
    @Test
    void rendersCancellationReasonFromTheEvent() {
        var order = savedOrder();
        order.cancel("payment declined");
        orderChanges.commit(order);

        orderEventPublishUseCase.publishPending();

        var captor = ArgumentCaptor.forClass(NotificationEventRequest.class);
        verify(notificationClient).notify(captor.capture());
        assertThat(captor.getValue().body()).contains("payment declined");
    }

    private void releaseBackoff() {
        jdbc.update("update order_events set next_attempt_after = null where publish_status = 'PENDING'");
    }

    private String statusOf(String orderId, String type) {
        return jdbc.queryForObject(
                "select publish_status from order_events where order_id = ? and type = ?",
                String.class, orderId, type);
    }

    private String eventIdOf(String orderId, String type) {
        return jdbc.queryForObject(
                "select id from order_events where order_id = ? and type = ?", String.class, orderId, type);
    }

    private Integer attemptsOf(String orderId, String type) {
        return jdbc.queryForObject(
                "select attempts from order_events where order_id = ? and type = ?",
                Integer.class, orderId, type);
    }

    private String lastErrorOf(String orderId, String type) {
        return jdbc.queryForObject(
                "select last_error from order_events where order_id = ? and type = ?",
                String.class, orderId, type);
    }

    private Order savedOrder() {
        var order = new Order(
                "mem_pub",
                List.of(new OrderLine("sku_tee_white_m", "prd_tee", "Tee", "White M", 1, Money.krw(29_000))),
                new Address("adr_pub", "home", "Demo Customer", "010", "1 Main", "Seoul", "04524", true)
        );
        orderChanges.commit(order);
        return order;
    }
}
