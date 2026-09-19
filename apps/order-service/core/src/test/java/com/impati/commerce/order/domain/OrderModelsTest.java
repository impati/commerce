package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderModelsTest {
    /**
     * [PD-0023-R1][PD-0023-R2][PD-0023-R3][PD-0023-R5] 허용되는 전이 순서와 상품 금액 계산을 잡는다.
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
     * 취소 사유가 길면 자른다.
     *
     * <p>사유는 하위 서비스의 예외 메시지가 그대로 들어오므로 길이가 통제되지 않는다. 자르지
     * 않으면 사건 저장이 컬럼 길이를 넘겨 실패하고, 사건과 같은 트랜잭션인 <b>취소 자체가
     * 롤백된다</b> — 사유가 길다는 이유로 주문이 취소되지 않는다.
     */
    @Test
    void truncatesLongCancelReason() {
        var order = newOrder();
        order.failCheckout("x".repeat(OrderEvent.MAX_REASON_LENGTH + 1_000));

        var event = order.drainPendingEvents().stream()
                .filter(candidate -> candidate.type() == OrderEventType.CHECKOUT_FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(event.payload().get("reason")).hasSize(OrderEvent.MAX_REASON_LENGTH);
    }

    /** 짧은 사유는 그대로 남는다. */
    @Test
    void keepsShortCancelReasonIntact() {
        var order = newOrder();
        order.failCheckout("payment declined");

        var event = order.drainPendingEvents().stream()
                .filter(candidate -> candidate.type() == OrderEventType.CHECKOUT_FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(event.payload().get("reason")).isEqualTo("payment declined");
    }

    /**
     * 발행 실패 메시지가 길면 자른다.
     *
     * <p>자르지 않으면 결과를 적는 UPDATE가 컬럼 길이를 넘겨 실패하고, 그 예외를 발행 루프가
     * 삼키므로 <b>attempts가 저장되지 않는다</b>. 같은 사건이 매 주기 다시 집혀 한도에 영영
     * 도달하지 못한다.
     */
    @Test
    void truncatesLongPublishError() {
        var order = newOrder();
        var event = order.drainPendingEvents().getFirst();

        event.markFailed("y".repeat(OrderEvent.MAX_ERROR_LENGTH + 1_000), 3);

        assertThat(event.lastError()).hasSize(OrderEvent.MAX_ERROR_LENGTH);
        assertThat(event.attempts()).isEqualTo(1);
    }

    /** 실패 메시지가 없어도 깨지지 않는다. 예외 메시지는 null일 수 있다. */
    @Test
    void toleratesNullPublishError() {
        var order = newOrder();
        var event = order.drainPendingEvents().getFirst();

        event.markFailed(null, 3);

        assertThat(event.lastError()).isNull();
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
