package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.order.application.model.OrderAddress;
import com.impati.commerce.order.application.model.OrderDetails;
import com.impati.commerce.order.application.model.OrderLineDetails;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.order.domain.CheckoutProgress;

/**
 * 도메인 모델을 유스케이스 결과로 옮긴다. 도메인은 결과 타입을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 볼 수 있으면 도메인 객체를 손에 넣어야 부를 수
 * 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 *
 * <p>{@link AddressResponse}가 남아 있는 것은 배송지를 member-service에서 받아오기
 * 때문이다. 나가는 방향의 계약이며 들어오는 방향과 구분된다.
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

    static OrderDetails toDetails(Order order) {
        return toDetails(order, null);
    }

    static OrderDetails toDetails(Order order, CheckoutProgress progress) {
        var address = order.shippingAddress();
        return new OrderDetails(
                order.id(),
                order.memberId(),
                order.status(),
                order.lines().stream().map(OrderMapper::toDetails).toList(),
                order.total(),
                new OrderAddress(
                        address.id(),
                        address.alias(),
                        address.recipient(),
                        address.phone(),
                        address.line1(),
                        address.city(),
                        address.postalCode(),
                        address.defaultAddress()
                ),
                order.paymentId(),
                order.shipmentId(),
                order.inventoryReservationId(),
                progress == null ? null : progress.outcome().name(),
                progress == null ? null : progress.paymentCleanupStatus(),
                progress == null ? null : progress.failureCode()
        );
    }

    static OrderLineDetails toDetails(OrderLine line) {
        return new OrderLineDetails(
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
