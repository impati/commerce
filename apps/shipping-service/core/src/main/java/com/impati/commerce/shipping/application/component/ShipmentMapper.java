package com.impati.commerce.shipping.application.component;

import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.domain.ShippingModels.Address;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;

/**
 * 도메인 모델과 유스케이스 입출력을 잇는다. 도메인은 그 타입들을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 볼 수 있으면 도메인 객체를 손에 넣어야 부를 수
 * 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 */
final class ShipmentMapper {
    private ShipmentMapper() {
    }

    static Address toAddress(ShipmentAddress address) {
        return new Address(
                address.id(),
                address.alias(),
                address.recipient(),
                address.phone(),
                address.line1(),
                address.city(),
                address.postalCode(),
                address.defaultAddress()
        );
    }

    static ShipmentDetails toDetails(Shipment shipment) {
        var address = shipment.address();
        return new ShipmentDetails(
                shipment.id(),
                shipment.orderId(),
                shipment.memberId(),
                new ShipmentAddress(
                        address.id(),
                        address.alias(),
                        address.recipient(),
                        address.phone(),
                        address.line1(),
                        address.city(),
                        address.postalCode(),
                        address.defaultAddress()
                ),
                shipment.status().name(),
                shipment.carrierCode(),
                shipment.carrierName(),
                shipment.trackingNumber()
        );
    }
}
