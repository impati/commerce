package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.OrderRepository;
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
 * 저장 후 다시 읽었을 때 aggregate가 그대로 복원되는지 확인한다.
 *
 * <p>인메모리 맵과 달리 조회는 새 객체를 만들어 돌려준다. 저장하지 않은 변경은 사라진다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:order-repo;DB_CLOSE_DELAY=-1")
class JdbcOrderRepositoryTest {
    @Autowired
    private OrderRepository orders;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsOrderWithLinesAndAddress() {
        var order = newOrder();
        order.attachReservation("rsv_round");
        orders.save(order);

        var loaded = orders.findById(order.id()).orElseThrow();

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
        orders.save(order);

        order.markPaid("pay_round");
        orders.save(order);
        order.attachShipment("shp_round");
        orders.save(order);

        var loaded = orders.findById(order.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo("FULFILLING");
        assertThat(loaded.paymentId()).isEqualTo("pay_round");
        assertThat(loaded.shipmentId()).isEqualTo("shp_round");
        assertThat(loaded.lines()).hasSize(2);
    }

    /** 저장하지 않은 변경은 반영되지 않는다. 인메모리 맵에서는 성립하지 않던 성질이다. */
    @Test
    void discardsChangesThatWereNotSaved() {
        var order = newOrder();
        orders.save(order);

        order.markPaid("pay_unsaved");

        assertThat(orders.findById(order.id()).orElseThrow().status()).isEqualTo("CREATED");
    }

    @Test
    void returnsEmptyForUnknownOrder() {
        assertThat(orders.findById("ord_never_saved")).isEmpty();
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
        order.markPaid("pay_column");
        orders.save(order);

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
        orders.save(order);

        var first = jdbc.queryForMap(
                "select sku_id, product_id, product_name, sku_name, quantity, unit_amount, unit_currency"
                        + " from order_lines where order_id = ? and line_no = 0",
                order.id()
        );

        assertThat(first.get("SKU_ID")).isEqualTo("sku_tee_white_m");
        assertThat(first.get("PRODUCT_ID")).isEqualTo("prd_tee");
        assertThat(first.get("PRODUCT_NAME")).isEqualTo("Everyday Cotton Tee");
        assertThat(first.get("SKU_NAME")).isEqualTo("White / M");
        assertThat(first.get("QUANTITY")).isEqualTo(2);
        assertThat(first.get("UNIT_AMOUNT")).isEqualTo(29_000L);
        assertThat(first.get("UNIT_CURRENCY")).isEqualTo("KRW");
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
