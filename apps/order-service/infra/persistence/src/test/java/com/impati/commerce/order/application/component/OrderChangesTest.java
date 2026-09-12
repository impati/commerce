package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.OrderWriter;
import com.impati.commerce.order.application.port.out.TransactionSection;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 주문의 상태와 그 상태를 만든 사건이 함께 성립하는지 확인한다 (ADR-0012).
 *
 * <p>이 응집 단위가 깨지면 결제된 주문에 알릴 의도가 없거나, 일어나지 않은 일이 소비자에게
 * 간다. 둘 다 예외 없이 조용히 일어난다.
 */
@SpringBootTest(properties = "orders.event-publish-interval=3600000")
@RequiresDatabase
class OrderChangesTest {

    @Autowired
    private OrderChanges orderChanges;

    @Autowired
    private OrderWriter orderWriter;

    @Autowired
    private TransactionSection transactionSection;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void commitsOrderAndItsEventsTogether() {
        var order = newOrder("mem_together");
        orderChanges.commit(order);

        assertThat(orderRepository.findById(order.id())).isPresent();
        assertThat(eventTypesOf(order.id())).containsExactly("ORDER_CREATED");
    }

    /**
     * 사건이 없는 변경도 같은 길로 간다.
     *
     * <p>부르는 쪽이 "이번엔 사건이 있나"를 판단하지 않는 것이 요점이다. 판단하게 만들면
     * 틀릴 자리가 생긴다.
     */
    @Test
    void commitsChangesThatProduceNoEvent() {
        var order = newOrder("mem_noevent");
        orderChanges.commit(order);

        order.attachReservation("rsv_noevent");
        orderChanges.commit(order);

        assertThat(orderRepository.findById(order.id()).orElseThrow().inventoryReservationId())
                .isEqualTo("rsv_noevent");
        assertThat(eventTypesOf(order.id())).containsExactly("ORDER_CREATED");
    }

    /** 같은 주문을 여러 번 확정해도 이미 넘긴 사건이 다시 쓰이지 않는다. */
    @Test
    void doesNotRewriteEventsAlreadyHandedOver() {
        var order = newOrder("mem_twice");
        orderChanges.commit(order);
        orderChanges.commit(order);

        assertThat(eventTypesOf(order.id())).containsExactly("ORDER_CREATED");
    }

    /**
     * 사건 저장이 실패하면 주문도 남지 않는다.
     *
     * <p>여기가 이 클래스의 존재 이유다. 둘이 갈라지면 결제된 주문에 알릴 의도가 없는 상태가
     * 되고, 그 상태는 아무 예외도 남기지 않는다.
     *
     * <p>실제 트랜잭션 어댑터를 쓰고 사건 저장만 실패시킨다. 대역으로 대신하면 롤백이 도는지가
     * 아니라 대역이 무엇을 불렀는지만 확인하게 된다.
     */
    @Test
    void rollsBackTheOrderWhenEventsCannotBeSaved() {
        var order = newOrder("mem_rollback");
        var failingEvents = mock(OrderEventRepository.class);
        doThrow(new IllegalStateException("event store down")).when(failingEvents).saveAll(any());
        var changes = new OrderChanges(orderWriter, failingEvents, transactionSection);

        assertThatThrownBy(() -> changes.commit(order)).isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.findById(order.id())).isEmpty();
        assertThat(eventTypesOf(order.id())).isEmpty();
    }

    private List<String> eventTypesOf(String orderId) {
        return jdbc.queryForList(
                "select type from order_events where order_id = ? order by seq", String.class, orderId);
    }

    private Order newOrder(String memberId) {
        return new Order(
                memberId,
                List.of(new OrderLine("sku_tee_white_m", "prd_tee", "Tee", "White M", 1, Money.krw(29_000))),
                new Address("adr_chg", "home", "Demo Customer", "010", "1 Main", "Seoul", "04524", true)
        );
    }
}
