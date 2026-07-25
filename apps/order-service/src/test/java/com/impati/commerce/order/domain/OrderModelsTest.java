package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderModelsTest {
    @Test
    void calculatesTotalAndMovesThroughPaidFulfillmentDelivery() {
        var address = new AddressResponse(
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
        order.markPaid("pay_demo");
        order.attachShipment("shp_demo");
        order.markDelivered();

        assertThat(order.total()).isEqualTo(Money.krw(58000));
        assertThat(order.toResponse().status()).isEqualTo("DELIVERED");
        assertThat(order.toResponse().inventoryReservationId()).isEqualTo("rsv_demo");
    }
}

