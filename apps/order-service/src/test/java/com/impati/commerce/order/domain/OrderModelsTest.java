package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        order.attachShipment("shp_demo", "TRK-shp_demo");
        order.markDelivered();

        assertThat(order.total()).isEqualTo(Money.krw(58000));
        assertThat(order.status()).isEqualTo("DELIVERED");
        assertThat(order.inventoryReservationId()).isEqualTo("rsv_demo");
        assertThat(order.shippingAddress().recipient()).isEqualTo("Demo Customer");
    }

    /**
     * [PD-0015-R2] 결제가 없는 주문은 결제 미확인으로 표시되지 않는다.
     *
     * <p>표시의 뜻은 "이 결제가 매입됐는지 모른다"이므로 물어볼 대상이 없으면 성립하지 않는다.
     * 그런 주문이 표시되면 정리가 무엇을 물어야 할지 알 수 없어 표시가 영영 남는다.
     */
    @Test
    void orderWithoutPaymentCannotBeMarkedUnknown() {
        var order = newOrder();
        order.cancel("test");

        assertThatThrownBy(order::markPaymentOutcomeUnknown)
                .isInstanceOf(DomainException.class);
    }

    /** [PD-0015-R4][PD-0015-R5] 표시를 해제해도 주문은 취소로 남는다. */
    @Test
    void resolvingTheMarkLeavesTheOrderCancelled() {
        var order = newOrder();
        order.attachPayment("pay_resolve");
        order.cancel("test");
        order.markPaymentOutcomeUnknown();

        order.resolvePaymentOutcome();

        assertThat(order.paymentOutcomeUnknown()).isFalse();
        assertThat(order.status()).isEqualTo("CANCELLED");
    }

    private Order newOrder() {
        return new Order(
                "mem_demo",
                List.of(new OrderLine(
                        "sku_tee_white_m",
                        "prd_tee",
                        "Everyday Cotton Tee",
                        "White / M",
                        2,
                        Money.krw(29000)
                )),
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
