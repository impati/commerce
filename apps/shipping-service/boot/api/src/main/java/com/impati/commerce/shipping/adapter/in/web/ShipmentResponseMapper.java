package com.impati.commerce.shipping.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.ApiContracts.ReturnShipmentResponse;
import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;

/**
 * 서비스 간 HTTP 계약과 유스케이스 입출력을 잇는다.
 *
 * <p>계약이 <b>서비스 간</b>의 것이므로 어댑터가 안다. 응용 계층이 알면 인바운드 어댑터가
 * 늘어날 때마다 응용이 바뀐다.
 */
final class ShipmentResponseMapper {
    private ShipmentResponseMapper() {
    }

    static ShipmentAddress toAddress(AddressResponse address) {
        return new ShipmentAddress(
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

    static ShipmentResponse from(ShipmentDetails shipment) {
        var address = shipment.address();
        return new ShipmentResponse(
                shipment.id(),
                shipment.orderId(),
                shipment.memberId(),
                new AddressResponse(
                        address.id(),
                        address.alias(),
                        address.recipient(),
                        address.phone(),
                        address.line1(),
                        address.city(),
                        address.postalCode(),
                        address.defaultAddress()
                ),
                shipment.status(),
                shipment.carrierCode(),
                shipment.carrierName(),
                shipment.trackingNumber()
        );
    }

    static ReturnShipmentResponse returnFrom(ShipmentDetails shipment) {
        return new ReturnShipmentResponse(
                shipment.id(), shipment.returnId(), shipment.orderId(), shipment.memberId(),
                from(shipment.address()), shipment.status(), shipment.carrierCode(), shipment.carrierName(),
                shipment.trackingNumber());
    }

    private static com.impati.commerce.common.ApiContracts.AddressResponse from(ShipmentAddress address) {
        return new com.impati.commerce.common.ApiContracts.AddressResponse(
                address.id(), address.alias(), address.recipient(), address.phone(), address.line1(), address.city(),
                address.postalCode(), address.defaultAddress());
    }
}
