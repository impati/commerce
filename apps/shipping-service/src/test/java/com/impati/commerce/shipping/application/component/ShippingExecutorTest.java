package com.impati.commerce.shipping.application.component;

import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 배송 상태 전이 규칙을 고정한다 (PD-0013).
 *
 * <p>허용되는 전이와 거절되는 전이를 함께 확인한다. 무엇을 허용하는지는 사용 중에 드러나지만
 * 무엇을 거절해야 하는지는 드러나지 않는다.
 */
@SpringBootTest
@RequiresDatabase
class ShippingExecutorTest {

    @Autowired
    private ShippingUseCase shippingUseCase;

    /** PD-0013-R1: 배송은 준비 상태로 만들어지고 운송장 번호가 이때 발급된다. */
    @Test
    void newShipmentIsReadyWithTrackingNumber() {
        var shipment = create("ord_ready");

        assertThat(shipment.status()).isEqualTo("READY");
        assertThat(shipment.trackingNumber()).isNotBlank();
    }

    /** PD-0013-R5: 준비 상태의 배송은 취소할 수 있다. */
    @Test
    void readyShipmentCanBeCancelled() {
        var shipment = create("ord_cancel");

        var cancelled = shippingUseCase.cancel(shipment.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    /** PD-0013-R5: 출고된 배송은 취소할 수 없다. 회수는 반품이며 다른 절차다. */
    @Test
    void shippedShipmentCannotBeCancelled() {
        var shipment = create("ord_shipped_cancel");
        shippingUseCase.ship(shipment.id());

        assertThatThrownBy(() -> shippingUseCase.cancel(shipment.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already left");
    }

    /** PD-0013-R5: 완료된 배송도 취소할 수 없다. */
    @Test
    void deliveredShipmentCannotBeCancelled() {
        var shipment = create("ord_delivered_cancel");
        shippingUseCase.deliver(shipment.id());

        assertThatThrownBy(() -> shippingUseCase.cancel(shipment.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already left");
    }

    /** PD-0013-R6: 취소된 배송은 출고할 수 없다. */
    @Test
    void cancelledShipmentCannotBeShipped() {
        var shipment = create("ord_cancelled_ship");
        shippingUseCase.cancel(shipment.id());

        assertThatThrownBy(() -> shippingUseCase.ship(shipment.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not ready");
    }

    /** PD-0013-R6: 취소된 배송은 완료할 수 없다. 취소는 끝 상태다. */
    @Test
    void cancelledShipmentCannotBeDelivered() {
        var shipment = create("ord_cancelled_deliver");
        shippingUseCase.cancel(shipment.id());

        assertThatThrownBy(() -> shippingUseCase.deliver(shipment.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be delivered");
    }

    /** PD-0013-R7: 취소 요청이 여러 번 도착해도 첫 결과를 유지한다. */
    @Test
    void cancelIsIdempotent() {
        var shipment = create("ord_idem_cancel");

        var first = shippingUseCase.cancel(shipment.id());
        var second = shippingUseCase.cancel(shipment.id());

        assertThat(second.status()).isEqualTo("CANCELLED");
        assertThat(second).isEqualTo(first);
    }

    /** PD-0013-R2: 출고는 준비 상태에서만 할 수 있다. */
    @Test
    void shippingTwiceIsRejected() {
        var shipment = create("ord_twice_ship");
        shippingUseCase.ship(shipment.id());

        assertThatThrownBy(() -> shippingUseCase.ship(shipment.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not ready");
    }

    /** PD-0013-R3: 출고를 기록하지 않아도 완료할 수 있다. */
    @Test
    void deliveringWithoutShippingIsAllowed() {
        var shipment = create("ord_direct_deliver");

        assertThat(shippingUseCase.deliver(shipment.id()).status()).isEqualTo("DELIVERED");
    }

    private ShipmentDetails create(String orderId) {
        return shippingUseCase.create(orderId, "mem_demo", new ShipmentAddress(
                "adr_1", "home", "받는이", "010-0000-0000", "서울 어딘가 1", "서울", "01234", true
        ));
    }
}
