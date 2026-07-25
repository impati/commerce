package com.impati.commerce.order.application;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.OrderLineResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다.
 *
 * <p>도메인은 {@code XxxRequest}/{@code XxxResponse}를 모른다. 그 변환을 여기로 모아둔다.
 */
final class OrderMapper {
    private OrderMapper() {
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

    static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.id(),
                order.memberId(),
                order.status(),
                order.lines().stream().map(OrderMapper::toResponse).toList(),
                order.total(),
                toResponse(order.shippingAddress()),
                order.paymentId(),
                order.shipmentId(),
                order.inventoryReservationId()
        );
    }

    static OrderLineResponse toResponse(OrderLine line) {
        return new OrderLineResponse(
                line.skuId(),
                line.productId(),
                line.productName(),
                line.skuName(),
                line.quantity(),
                line.unitPrice(),
                line.lineTotal()
        );
    }
}
