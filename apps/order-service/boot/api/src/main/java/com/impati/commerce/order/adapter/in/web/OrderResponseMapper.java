package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.OrderLineResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.order.application.port.in.CheckoutResult;
import com.impati.commerce.order.application.port.in.OrderAddress;
import com.impati.commerce.order.application.port.in.OrderDetails;
import com.impati.commerce.order.application.port.in.OrderLineDetails;

/**
 * 유스케이스 결과를 서비스 간 HTTP 계약으로 옮긴다.
 *
 * <p>계약이 <b>서비스 간</b>의 것이므로 어댑터가 안다. 응용 계층이 알면 인바운드 어댑터가
 * 늘어날 때마다 응용이 바뀐다.
 */
final class OrderResponseMapper {
    private OrderResponseMapper() {
    }

    static CheckoutResponse from(CheckoutResult result) {
        return new CheckoutResponse(from(result.order()), result.payment(), result.shipment());
    }

    static OrderResponse from(OrderDetails order) {
        return new OrderResponse(
                order.id(),
                order.memberId(),
                order.status(),
                order.lines().stream().map(OrderResponseMapper::from).toList(),
                order.total(),
                from(order.shippingAddress()),
                order.paymentId(),
                order.shipmentId(),
                order.inventoryReservationId()
        );
    }

    private static OrderLineResponse from(OrderLineDetails line) {
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

    private static AddressResponse from(OrderAddress address) {
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
}
