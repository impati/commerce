package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장 후 다시 읽었을 때 aggregate가 그대로 복원되는지 확인한다.
 *
 * <p>인메모리 맵과 달리 조회는 새 객체를 만들어 돌려준다. 저장하지 않은 변경은 사라진다.
 */
@SpringBootTest(properties = {
        // 사건 발행 릴레이를 끈다 (BL-0049: 끄는 것이 규율에 달려 있다).
        "orders.event-publish-interval=3600000"
})
@RequiresDatabase
class JdbcOrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderChanges orderChanges;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsOrderWithLinesAndAddress() {
        var order = newOrder();
        order.attachReservation("rsv_round");
        orderChanges.commit(order);

        var loaded = orderRepository.findById(order.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(order.id());
        assertThat(loaded.memberId()).isEqualTo("mem_demo");
        assertThat(loaded.status()).isEqualTo("CREATED");
        assertThat(loaded.inventoryReservationId()).isEqualTo("rsv_round");
        assertThat(loaded.total()).isEqualTo(Money.krw(58_000 + 87_000));
        assertThat(loaded.lines()).hasSize(2);
        assertThat(loaded.lines().getFirst().skuId()).isEqualTo("sku_tee_white_m");
        assertThat(loaded.lines().getFirst().unitPrice()).isEqualTo(Money.krw(29_000));
        assertThat(loaded.shippingAddress().recipient()).isEqualTo("Demo Customer");
        assertThat(loaded.shippingAddress().postalCode()).isEqualTo("04524");
    }

    @Test
    void savesStatusTransitionsOnTheSameRow() {
        var order = newOrder();
        orderChanges.commit(order);

        order.attachPayment("pay_round");

        order.markPaid();
        orderChanges.commit(order);
        order.attachShipment("shp_round", "TRK-shp_round");
        orderChanges.commit(order);

        var loaded = orderRepository.findById(order.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo("FULFILLING");
        assertThat(loaded.paymentId()).isEqualTo("pay_round");
        assertThat(loaded.shipmentId()).isEqualTo("shp_round");
        assertThat(loaded.lines()).hasSize(2);
    }

    /** 저장하지 않은 변경은 반영되지 않는다. 인메모리 맵에서는 성립하지 않던 성질이다. */
    @Test
    void discardsChangesThatWereNotSaved() {
        var order = newOrder();
        orderChanges.commit(order);

        order.attachPayment("pay_unsaved");

        order.markPaid();

        assertThat(orderRepository.findById(order.id()).orElseThrow().status()).isEqualTo("CREATED");
    }

    @Test
    void returnsEmptyForUnknownOrder() {
        assertThat(orderRepository.findById("ord_never_saved")).isEmpty();
    }

    /** PD-0012-R12: 매입 결과를 확인하지 못한 주문은 표시가 남고 다시 찾힌다. */
    @Test
    void keepsAndFindsTheUnknownPaymentOutcomeMark() {
        var order = newOrder();
        order.attachPayment("pay_unknown");
        order.cancel("test");
        order.markPaymentOutcomeUnknown();
        orderChanges.commit(order);

        assertThat(orderRepository.findById(order.id()).orElseThrow().paymentOutcomeUnknown()).isTrue();
        assertThat(flagColumn(order.id(), "payment_outcome_unknown")).isTrue();
        assertThat(orderRepository.findPaymentReconciliationCandidates(100)).contains(order.id());
    }

    /** 정리가 끝난 주문은 다시 조회 대상이 되지 않는다. 아니면 목록이 영원히 줄지 않는다. */
    @Test
    void resolvedOrderLeavesTheUnknownPaymentOutcomeList() {
        var order = newOrder();
        order.attachPayment("pay_resolved");
        order.cancel("test");
        order.markPaymentOutcomeUnknown();
        orderChanges.commit(order);

        order.resolvePaymentOutcome();
        orderChanges.commit(order);

        assertThat(flagColumn(order.id(), "payment_outcome_unknown")).isFalse();
        assertThat(orderRepository.findPaymentReconciliationCandidates(100)).doesNotContain(order.id());
    }

    /** 평범한 주문에는 표시가 붙지 않는다. 기본값이 반대면 모든 주문이 정리 대상이 된다. */
    @Test
    void ordinaryOrderIsNotMarked() {
        var order = newOrder();
        orderChanges.commit(order);

        assertThat(flagColumn(order.id(), "payment_outcome_unknown")).isFalse();
        assertThat(orderRepository.findPaymentReconciliationCandidates(100)).doesNotContain(order.id());
    }

    /**
     * [PD-0015-R7] 같은 주문을 두 번 점유할 수 없다.
     *
     * <p>정리하는 인스턴스가 여러 개인 것을 전제하므로 이것이 유일한 배타성 수단이다. 깨지면
     * 장애 중인 결제 서비스에 인스턴스 수만큼 요청이 곱해진다.
     */
    @Test
    void claimSucceedsOnlyOnce() {
        var order = markedOrder("pay_claim_once");

        var first = orderRepository.claimForPaymentReconciliation(order.id(), Duration.ofMinutes(1));
        var second = orderRepository.claimForPaymentReconciliation(order.id(), Duration.ofMinutes(1));

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    /** [PD-0015-R8] 점유가 다음 시도 시각을 미래로 밀어 후보에서 빠진다. 이것이 실패 백오프다. */
    @Test
    void claimPushesTheOrderOutOfTheCandidateList() {
        var order = markedOrder("pay_claim_backoff");
        assertThat(orderRepository.findPaymentReconciliationCandidates(100)).contains(order.id());

        orderRepository.claimForPaymentReconciliation(order.id(), Duration.ofMinutes(1));

        assertThat(column(order.id(), "payment_reconcile_after")).isNotNull();
        assertThat(orderRepository.findPaymentReconciliationCandidates(100)).doesNotContain(order.id());
    }

    /** 표시되지 않은 주문은 점유되지 않는다. 정리가 이미 끝난 주문을 다시 집으면 안 된다. */
    @Test
    void unmarkedOrderCannotBeClaimed() {
        var order = newOrder();
        orderChanges.commit(order);

        assertThat(orderRepository.claimForPaymentReconciliation(order.id(), Duration.ofMinutes(1))).isEmpty();
    }

    /**
     * 애그리거트 저장이 점유를 지우지 않는다.
     *
     * <p>{@code save}는 행 전체를 덮어쓰는데 점유 컬럼은 도메인에 없다. 그대로 쓰면 정리 중의
     * 어떤 저장이든 점유를 날려 다른 인스턴스가 같은 주문을 집는다.
     */
    @Test
    void savingAMarkedOrderKeepsTheClaim() {
        var order = markedOrder("pay_claim_kept");
        orderRepository.claimForPaymentReconciliation(order.id(), Duration.ofMinutes(1));
        var claimedAt = column(order.id(), "payment_reconcile_after");

        orderChanges.commit(order);

        assertThat(column(order.id(), "payment_reconcile_after")).isEqualTo(claimedAt);
    }

    /** 표시가 해제되면 점유도 함께 비워진다. 남겨두면 다시 표시될 때 그 값이 되살아난다. */
    @Test
    void resolvingTheMarkClearsTheClaim() {
        var order = markedOrder("pay_claim_cleared");
        orderRepository.claimForPaymentReconciliation(order.id(), Duration.ofMinutes(1));

        order.resolvePaymentOutcome();
        orderChanges.commit(order);

        assertThat(column(order.id(), "payment_reconcile_after")).isNull();
    }

    private Order markedOrder(String paymentId) {
        var order = newOrder();
        order.attachPayment(paymentId);
        order.cancel("test");
        order.markPaymentOutcomeUnknown();
        orderChanges.commit(order);
        return order;
    }

    /**
     * 각 값이 자기 컬럼에 들어갔는지 직접 확인한다.
     *
     * <p>왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 예를 들어 city와 postalCode를
     * 서로 바꿔 쓰고 읽으면 복원된 객체는 멀쩡하지만 DB의 ship_city에는 우편번호가 들어 있다.
     * varchar가 연속된 구간에서는 이런 실수가 컴파일도 DB도 통과하므로 컬럼을 직접 읽어 막는다.
     */
    @Test
    void writesEachAddressFieldToItsOwnColumn() {
        var order = newOrder();
        order.attachPayment("pay_column");
        order.markPaid();
        orderChanges.commit(order);

        assertThat(column(order.id(), "ship_address_id")).isEqualTo("addr_demo");
        assertThat(column(order.id(), "ship_alias")).isEqualTo("home");
        assertThat(column(order.id(), "ship_recipient")).isEqualTo("Demo Customer");
        assertThat(column(order.id(), "ship_phone")).isEqualTo("010-0000-0000");
        assertThat(column(order.id(), "ship_line1")).isEqualTo("123 Commerce Road");
        assertThat(column(order.id(), "ship_city")).isEqualTo("Seoul");
        assertThat(column(order.id(), "ship_postal_code")).isEqualTo("04524");
        assertThat(column(order.id(), "member_id")).isEqualTo("mem_demo");
        assertThat(column(order.id(), "status")).isEqualTo("PAID");
        assertThat(column(order.id(), "payment_id")).isEqualTo("pay_column");
    }

    @Test
    void writesEachLineFieldToItsOwnColumn() {
        var order = newOrder();
        orderChanges.commit(order);

        var first = jdbc.queryForMap(
                "select sku_id, product_id, product_name, sku_name, quantity, unit_amount, unit_currency"
                        + " from order_lines where order_id = ? and line_no = 0",
                order.id()
        );

        assertThat(first.get("sku_id")).isEqualTo("sku_tee_white_m");
        assertThat(first.get("product_id")).isEqualTo("prd_tee");
        assertThat(first.get("product_name")).isEqualTo("Everyday Cotton Tee");
        assertThat(first.get("sku_name")).isEqualTo("White / M");
        assertThat(first.get("quantity")).isEqualTo(2);
        assertThat(first.get("unit_amount")).isEqualTo(29_000L);
        assertThat(first.get("unit_currency")).isEqualTo("KRW");
    }

    /** boolean 컬럼은 String으로 읽으면 "TRUE"/"FALSE"가 되므로 타입을 명시해 읽는다. */
    private boolean flagColumn(String orderId, String columnName) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select " + columnName + " from orders where id = ?",
                Boolean.class,
                orderId
        ));
    }

    private String column(String orderId, String columnName) {
        return jdbc.queryForObject(
                "select " + columnName + " from orders where id = ?",
                String.class,
                orderId
        );
    }

    private Order newOrder() {
        return new Order(
                "mem_demo",
                List.of(
                        new OrderLine("sku_tee_white_m", "prd_tee", "Everyday Cotton Tee", "White / M", 2,
                                Money.krw(29_000)),
                        new OrderLine("sku_drip_ivory", "prd_drip", "Ceramic Drip Set", "Ivory", 1,
                                Money.krw(87_000))
                ),
                new Address(
                        "addr_demo",
                        "home",
                        "Demo Customer",
                        "010-0000-0000",
                        "123 Commerce Road",
                        "Seoul",
                        "04524",
                        true
                )
        );
    }
}
