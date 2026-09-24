package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.ConcurrentOrderModificationException;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.order.domain.OrderModels.OrderStatus;
import com.impati.commerce.order.domain.PriceBreakdown;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(loaded.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(loaded.inventoryReservationId()).isEqualTo("rsv_round");
        assertThat(loaded.total()).isEqualTo(Money.krw(58_000 + 87_000));
        assertThat(loaded.priceBreakdown()).isEqualTo(new PriceBreakdown(
                Money.krw(58_000 + 87_000), Money.krw(0), Money.krw(58_000 + 87_000)));
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
        assertThat(loaded.status()).isEqualTo(OrderStatus.FULFILLING);
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

        assertThat(orderRepository.findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void returnsEmptyForUnknownOrder() {
        assertThat(orderRepository.findById("ord_never_saved")).isEmpty();
    }

    @Test
    void staleShipmentEventCannotOverwriteAConcurrentlyCancelledOrder() {
        var order = newOrder();
        order.attachPayment("pay_concurrent");
        order.markPaid();
        order.attachShipment("shp_concurrent", null);
        orderChanges.commit(order);
        var cancellation = orderRepository.findById(order.id()).orElseThrow();
        var staleEventProjection = orderRepository.findById(order.id()).orElseThrow();

        cancellation.cancelByCustomer();
        orderChanges.commit(cancellation);
        staleEventProjection.applyShipmentEvent("SHIPMENT_REGISTERED", "shp_concurrent", Map.of(),
                OffsetDateTime.parse("2026-09-24T00:00:00Z"));

        assertThatThrownBy(() -> orderChanges.commit(staleEventProjection))
                .isInstanceOf(ConcurrentOrderModificationException.class);
        assertThat(orderRepository.findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
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
        assertThat(column(order.id(), "product_amount")).isEqualTo("145000");
        assertThat(column(order.id(), "shipping_fee_amount")).isEqualTo("0");
        assertThat(column(order.id(), "total_amount")).isEqualTo("145000");
        assertThat(column(order.id(), "amount_currency")).isEqualTo("KRW");
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
