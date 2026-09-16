package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.OrderDetailResponse;
import com.impati.commerce.common.ApiContracts.OrderLineResponse;
import com.impati.commerce.common.ApiContracts.OrderPageResponse;
import com.impati.commerce.common.ApiContracts.OrderShippingAddressResponse;
import com.impati.commerce.common.ApiContracts.OrderSummaryResponse;
import com.impati.commerce.common.ApiContracts.OrderTimelineResponse;
import com.impati.commerce.order.application.port.in.OrderHistoryDetail;
import com.impati.commerce.order.application.port.in.OrderPage;

final class OrderHistoryResponseMapper {
    private OrderHistoryResponseMapper() { }

    static OrderPageResponse from(OrderPage page) {
        return new OrderPageResponse(page.items().stream().map(item -> new OrderSummaryResponse(item.id(),
                item.orderedAt(), item.representativeProductName(), item.representativeSkuName(),
                item.additionalProductCount(), item.totalQuantity(), item.total(), item.checkoutResult(), item.orderStatus()))
                .toList(), page.nextCursor());
    }

    static OrderDetailResponse from(OrderHistoryDetail detail) {
        var address = detail.shippingAddress();
        return new OrderDetailResponse(detail.id(), detail.orderedAt(), detail.checkoutResult(), detail.orderStatus(),
                detail.lines().stream().map(line -> new OrderLineResponse(line.skuId(), line.productId(),
                        line.productName(), line.skuName(), line.quantity(), line.unitPrice(), line.lineTotal())).toList(),
                detail.total(), new OrderShippingAddressResponse(address.recipient(), address.phone(), address.line1(),
                        address.city(), address.postalCode()), detail.trackingNumber(),
                detail.timeline().stream().map(event -> new OrderTimelineResponse(event.type(), event.occurredAt())).toList());
    }
}
