package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderModelsTest {
    /**
     * [PD-0003-R1][PD-0003-R2][PD-0003-R3][PD-0003-R5] 허용되는 전이 순서와 총액 계산을 잡는다.
     *
     * <p>거절되는 전이는 하나도 보지 않는다. 결제 전 주문에 배송을 붙이거나 완료된 주문을
     * 취소하는 시도가 막히는지는 여기서 드러나지 않는다. BL-0030.
     */
    @Test
    void calculatesTotalAndMovesThroughPaidFulfillmentDelivery() {
        var address = new Address(
                "addr_demo",
                "home",
                "Demo Customer",
                "010-0000-0000",
                "123 Commerce Road",
                "Seoul",
                "04524",
                true
        );
        var order = new Order(
                "mem_demo",
                List.of(new OrderLine(
                        "sku_tee_white_m",
                        "prd_tee",
                        "Everyday Cotton Tee",
                        "White / M",
                        2,
                        Money.krw(29000)
                )),
                address
        );

        order.attachReservation("rsv_demo");
        order.attachPayment("pay_demo");
        order.markPaid();
        order.attachShipment("shp_demo");
        order.markDelivered();

        assertThat(order.total()).isEqualTo(Money.krw(58000));
        assertThat(order.status()).isEqualTo("DELIVERED");
        assertThat(order.inventoryReservationId()).isEqualTo("rsv_demo");
        assertThat(order.shippingAddress().recipient()).isEqualTo("Demo Customer");
    }
}
