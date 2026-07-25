package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
