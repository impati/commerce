package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태 전이가 사건으로 커밋되는지 확인한다 (ADR-0012).
 *
 * <p>컬럼 값을 직접 읽는다. 저장 후 조회해서 비교하면 쓰기와 읽기가 같은 방향으로 틀렸을 때
 * 그대로 통과한다.
 *
 * <p>정리기가 이 컨텍스트에서 돌지 않도록 인터벌을 크게 덮는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-events-record;DB_CLOSE_DELAY=-1",
        "orders.payment-reconcile-interval=3600000",
        // 릴레이도 끈다. 이 테스트는 사건이 PENDING으로 남아 있는 것을 단정하는데, 릴레이가
        // 집어가면 attempts와 next_attempt_after가 움직여 단정이 깨진다.
        "orders.event-publish-interval=3600000"
})
class OrderEventRecordingTest {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderChanges orderChanges;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recordsCreationAsAnEvent() {
        var order = newOrder();
        orderChanges.commit(order);

        assertThat(typesOf(order.id())).containsExactly("ORDER_CREATED");
        assertThat(payloadOf(order.id(), "ORDER_CREATED"))
                .contains("\"totalAmount\":\"" + (58_000 + 87_000) + "\"")
                .contains("\"totalCurrency\":\"KRW\"");
    }

    @Test
    void recordsEachStatusTransitionInOrder() {
        var order = newOrder();
        orderChanges.commit(order);

        order.attachPayment("pay_evt");
        order.markPaid();
        orderChanges.commit(order);

        order.attachShipment("shp_evt", "TRK-77");
        orderChanges.commit(order);

        order.markDelivered();
        orderChanges.commit(order);

        assertThat(typesOf(order.id()))
                .containsExactly("ORDER_CREATED", "ORDER_PAID", "SHIPMENT_CREATED", "ORDER_DELIVERED");
        assertThat(payloadOf(order.id(), "SHIPMENT_CREATED")).contains("\"trackingNumber\":\"TRK-77\"");
    }

    /**
     * 같은 주문을 여러 번 저장해도 사건이 늘어나지 않는다.
     *
     * <p>{@code checkout}이 한 주문을 여러 번 저장하므로 이 경로가 실재한다. 커밋된 사건을
     * 비우지 않으면 소비자가 같은 사실을 여러 번 본다.
     */
    @Test
    void doesNotRewriteAlreadyCommittedEvents() {
        var order = newOrder();
        orderChanges.commit(order);
        orderChanges.commit(order);
        orderChanges.commit(order);

        assertThat(typesOf(order.id())).containsExactly("ORDER_CREATED");
    }

    /** 복원한 주문은 이미 일어난 일을 사건으로 다시 만들지 않는다. */
    @Test
    void restoredOrderCarriesNoPendingEvents() {
        var order = newOrder();
        orderChanges.commit(order);

        var loaded = orderRepository.findById(order.id()).orElseThrow();
        assertThat(loaded.hasPendingEvents()).isFalse();

        orderChanges.commit(loaded);
        assertThat(typesOf(order.id())).containsExactly("ORDER_CREATED");
    }

    /** 사건은 PENDING으로 기록된다. 발행은 별도로 일어난다. */
    @Test
    void recordsEventsAsPending() {
        var order = newOrder();
        orderChanges.commit(order);

        var row = jdbc.queryForMap(
                "select publish_status, attempts, next_attempt_after, tx_id, member_id"
                        + " from order_events where order_id = ?", order.id());
        assertThat(row.get("publish_status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("next_attempt_after")).isNull();
        assertThat(row.get("tx_id")).isNull();
        assertThat(row.get("member_id")).isEqualTo("mem_demo");
    }

    /**
     * 취소 사유가 사건에 남는다.
     *
     * <p>주문이 들고 있지 않은 값이지만 취소됐다는 사실의 일부다. 없으면 소비자가 사용자에게
     * 왜 취소됐는지 설명할 수 없다.
     */
    @Test
    void recordsCancellationReason() {
        var order = newOrder();
        orderChanges.commit(order);

        order.cancel("payment declined");
        orderChanges.commit(order);

        assertThat(payloadOf(order.id(), "ORDER_CANCELLED")).contains("\"reason\":\"payment declined\"");
    }

    private List<String> typesOf(String orderId) {
        return jdbc.queryForList(
                "select type from order_events where order_id = ? order by seq", String.class, orderId);
    }

    private String payloadOf(String orderId, String type) {
        return jdbc.queryForObject(
                "select payload from order_events where order_id = ? and type = ?",
                String.class, orderId, type);
    }

    private Order newOrder() {
        return new Order(
                "mem_demo",
                List.of(
                        new OrderLine("sku_tee_white_m", "prd_tee", "Tee", "White / M", 2, Money.krw(29_000)),
                        new OrderLine("sku_cap_black", "prd_cap", "Cap", "Black", 3, Money.krw(29_000))
                ),
                new Address("adr_evt", "home", "Demo Customer", "010-0000-0000",
                        "Seoul", "Seoul", "04524", true)
        );
    }
}
