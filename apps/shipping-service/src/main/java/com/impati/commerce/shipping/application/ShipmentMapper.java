package com.impati.commerce.shipping.application;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.shipping.domain.ShippingModels.Address;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다. 도메인은 계약을 모른다.
 */
final class ShipmentMapper {
    private ShipmentMapper() {
    }

    static Address toAddress(AddressResponse response) {
        return new Address(
                response.id(),
                response.alias(),
                response.recipient(),
                response.phone(),
                response.line1(),
                response.city(),
                response.postalCode(),
                response.defaultAddress()
        );
    }

    static AddressResponse toResponse(Address address) {
        return new AddressResponse(
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

    static ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(
                shipment.id(),
                shipment.orderId(),
                shipment.memberId(),
                toResponse(shipment.address()),
                shipment.status(),
                shipment.trackingNumber()
        );
    }
}
