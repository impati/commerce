package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.port.in.OrderEventPublishUseCase;
import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.BeforeEach;
import com.impati.commerce.test.RequiresDatabase;
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
        "orders.event-publish-max-attempts=3"
})
@RequiresDatabase
class OrderEventPublishTest {

    @Autowired
    private OrderEventPublishUseCase orderEventPublishUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderChanges orderChanges;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 발행 포트를 대역으로 쓴다.
     *
     * <p>여기서 잡는 것은 릴레이의 행동이다 — 무엇을 집고, 어떤 순서로 보내고, 실패를 어떻게
     * 남기는가. 브로커에 실제로 어떻게 실리는지는 발행 어댑터의 테스트가 본다.
     */
    @MockBean
    private OrderEventPublisher orderEventPublisher;

    /** 사건 테이블이 테스트 간에 공유된다. 앞선 테스트가 남긴 대기 사건이 이 주기에 딸려온다. */
    @BeforeEach
    void setUp() {
        reset(orderEventPublisher);
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

    /** 사건이 그대로 포트로 넘어간다. 응용 계층은 그것을 무엇으로 옮길지 모른다. */
    @Test
    void handsTheEventItselfToThePort() {
        var order = savedOrder();
        order.attachPayment("pay_key");
        order.markPaid();
        orderChanges.commit(order);

        orderEventPublishUseCase.publishPending();

        var captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(event -> event.type().name())
                .containsExactly("ORDER_CREATED", "ORDER_PAID");
        assertThat(captor.getAllValues().get(1).id()).isEqualTo(eventIdOf(order.id(), "ORDER_PAID"));
    }

    /**
     * 소비자가 없는 사건도 발행한다.
     *
     * <p>예전에는 발행 어댑터가 알림 없는 사건을 걸러 보내지 않았다. 대역이 발행자와 소비자를
     * 겸했기 때문이며, 브로커에서는 <b>누가 관심 있는지 발행자가 알 수 없다.</b> 걸러 보내면
     * 두 번째 소비자가 그 사건을 영원히 받지 못한다 (ADR-0016).
     */
    @Test
    void publishesEventsEvenWhenNoConsumerWantsThem() {
        var order = savedOrder();

        var settled = orderEventPublishUseCase.publishPending();

        assertThat(settled).isEqualTo(1);
        assertThat(statusOf(order.id(), "ORDER_CREATED")).isEqualTo("PUBLISHED");
        verify(orderEventPublisher, times(1)).publish(any());
    }

    /**
     * 실패가 상태로 남고 한도를 넘기면 FAILED로 끝난다.
     *
     * <p>예전에는 이 실패가 어댑터에서 사라져 건수조차 셀 수 없었다.
     */
    @Test
    void recordsFailureAndGivesUpAtTheLimit() {
        // ORDER_PAID만 실패시킨다. 전부 실패시키면 앞선 ORDER_CREATED에서 막혀 ORDER_PAID가
        // 시도조차 되지 않는다 — 순서 보장의 결과이며 여기서 보려는 것은 재시도와 종단이다.
        doThrow(new IllegalStateException("broker down"))
                .when(orderEventPublisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event != null && event.type() == OrderEventType.ORDER_PAID));
        var order = savedOrder();
        order.attachPayment("pay_fail");
        order.markPaid();
        orderChanges.commit(order);

        // 첫 주기가 두 사건을 집는다. ORDER_CREATED는 나가고 ORDER_PAID만 실패로 남는다.
        orderEventPublishUseCase.publishPending();
        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("PENDING");
        assertThat(attemptsOf(order.id(), "ORDER_PAID")).isEqualTo(1);
        assertThat(lastErrorOf(order.id(), "ORDER_PAID")).contains("broker down");

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

    /**
     * 한 건이 실패하면 <b>같은 주문의 뒤 사건이 나가지 않는다</b> (ADR-0016).
     *
     * <p>이 성질이 순서 보장의 절반이다. 나머지 절반은 점유가 만든다 — 한 주문을 한 인스턴스만
     * 갖는 것. 앞 건을 건너뛰고 뒤 건을 보내면 소비자가 보는 순서가 일어난 순서와 달라진다.
     */
    @Test
    void failureStopsTheRestOfThatOrder() {
        var order = savedOrder();
        order.attachPayment("pay_iso");
        order.markPaid();
        orderChanges.commit(order);
        order.attachShipment("shp_iso", "TRK-ISO");
        orderChanges.commit(order);

        doThrow(new IllegalStateException("only paid fails"))
                .when(orderEventPublisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event != null && event.type() == OrderEventType.ORDER_PAID));

        orderEventPublishUseCase.publishPending();

        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("PENDING");
        assertThat(statusOf(order.id(), "SHIPMENT_CREATED"))
                .as("앞 건이 막혔으면 뒤 건도 나가면 안 된다")
                .isEqualTo("PENDING");
        // ORDER_CREATED가 나가고 ORDER_PAID에서 막힌다 — 그 실패한 시도까지 둘이다.
        // SHIPMENT_CREATED는 시도조차 하지 않는다.
        verify(orderEventPublisher, times(2)).publish(any());
    }

    /**
     * 종단된 사건이 그 주문의 뒤 사건을 <b>영구히</b> 막는다 (ADR-0016).
     *
     * <p>주기 안의 멈춤만으로는 부족하다. 한도를 넘겨 FAILED가 되면 그 사건은 더 이상 PENDING이
     * 아니고, 다음 주기는 테이블을 새로 읽으므로 <b>"앞에 못 나간 것이 있다"는 사실이 사라진다.</b>
     * 그러면 뒤 사건이 자유롭게 나가고 소비자는 결제 없는 배송을 본다.
     *
     * <p>막는 것이 후보 질의의 일인 이유가 이것이다 — 멈춤이 코드에만 있고 데이터에 없으면
     * 주기를 넘기지 못한다.
     */
    @Test
    void aFailedEventBlocksTheRestOfThatOrderForever() {
        doThrow(new IllegalStateException("poison message"))
                .when(orderEventPublisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event != null && event.type() == OrderEventType.ORDER_PAID));

        var order = savedOrder();
        order.attachPayment("pay_poison");
        order.markPaid();
        orderChanges.commit(order);
        order.attachShipment("shp_poison", "TRK-POISON");
        orderChanges.commit(order);

        // 한도(3)까지 실패시킨다. 매 주기 백오프를 풀어 다음 주기를 흉내 낸다.
        for (var attempt = 0; attempt < 3; attempt++) {
            releaseBackoff();
            orderEventPublishUseCase.publishPending();
        }
        assertThat(statusOf(order.id(), "ORDER_PAID")).isEqualTo("FAILED");

        // 종단된 뒤로도 뒤 사건은 나가지 않는다. 여기가 본론이다.
        releaseBackoff();
        assertThat(orderEventPublishUseCase.publishPending())
                .as("FAILED가 남은 주문은 후보가 되면 안 된다")
                .isZero();
        assertThat(statusOf(order.id(), "SHIPMENT_CREATED"))
                .as("앞 사건이 영영 못 나갔으면 뒤 사건도 나가면 안 된다")
                .isEqualTo("PENDING");
    }

    /**
     * 한 주문의 실패가 다른 주문을 막지 않는다.
     *
     * <p>순서를 지켜야 하는 범위가 주문 안이므로 주문 사이에는 서로 영향이 없다. 이것까지
     * 막으면 사건 하나가 전체를 멈춘다.
     */
    @Test
    void failureOnOneOrderDoesNotBlockAnother() {
        var blocked = savedOrder();
        blocked.attachPayment("pay_blocked");
        blocked.markPaid();
        orderChanges.commit(blocked);

        var healthy = savedOrder();
        healthy.attachPayment("pay_healthy");
        healthy.markPaid();
        orderChanges.commit(healthy);

        doThrow(new IllegalStateException("only the blocked order fails"))
                .when(orderEventPublisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event != null && blocked.id().equals(event.orderId())));

        orderEventPublishUseCase.publishPending();

        assertThat(statusOf(blocked.id(), "ORDER_PAID")).isEqualTo("PENDING");
        assertThat(statusOf(healthy.id(), "ORDER_PAID")).isEqualTo("PUBLISHED");
    }

    /**
     * 취소 사유가 사건에 실려 나간다.
     *
     * <p>그 사유로 어떤 문구를 만들지는 여기의 관심이 아니다 — 사건은 사실만 담고 문구는
     * 소비자가 만든다 (ADR-0016).
     */
    @Test
    void carriesTheCheckoutFailureReasonOnTheEvent() {
        var order = savedOrder();
        order.failCheckout("payment declined");
        orderChanges.commit(order);

        orderEventPublishUseCase.publishPending();

        var captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(event -> event.type() == OrderEventType.CHECKOUT_FAILED)
                .singleElement()
                .satisfies(event -> assertThat(event.payload()).containsEntry("reason", "payment declined"));
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
