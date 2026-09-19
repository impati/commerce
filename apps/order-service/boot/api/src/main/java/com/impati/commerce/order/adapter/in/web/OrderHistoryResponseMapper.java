package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.OrderDetailResponse;
import com.impati.commerce.common.ApiContracts.OrderLineResponse;
import com.impati.commerce.common.ApiContracts.OrderPageResponse;
import com.impati.commerce.common.ApiContracts.OrderShippingAddressResponse;
import com.impati.commerce.common.ApiContracts.OrderSummaryResponse;
import com.impati.commerce.common.ApiContracts.OrderTimelineResponse;
import com.impati.commerce.common.ApiContracts.PriceBreakdownResponse;
import com.impati.commerce.order.application.model.PriceBreakdownDetails;
import com.impati.commerce.order.application.model.OrderHistoryDetail;
import com.impati.commerce.order.application.model.OrderPage;

final class OrderHistoryResponseMapper {
    private OrderHistoryResponseMapper() { }

    static OrderPageResponse from(OrderPage page) {
        return new OrderPageResponse(page.items().stream().map(item -> new OrderSummaryResponse(item.id(),
                item.orderedAt(), item.representativeProductName(), item.representativeSkuName(),
                item.additionalProductCount(), item.totalQuantity(), item.total(), priceBreakdown(item.priceBreakdown()),
                item.checkoutResult(), item.orderStatus(), item.cancellationStatus()))
                .toList(), page.nextCursor());
    }

    static OrderDetailResponse from(OrderHistoryDetail detail) {
        var address = detail.shippingAddress();
        return new OrderDetailResponse(detail.id(), detail.orderedAt(), detail.checkoutResult(), detail.orderStatus(),
                detail.cancellationStatus(), detail.cancellable(),
                detail.lines().stream().map(line -> new OrderLineResponse(line.skuId(), line.productId(),
                        line.productName(), line.skuName(), line.quantity(), line.unitPrice(), line.lineTotal())).toList(),
                detail.total(), priceBreakdown(detail.priceBreakdown()),
                new OrderShippingAddressResponse(address.recipient(), address.phone(), address.line1(),
                        address.city(), address.postalCode()), detail.trackingNumber(),
                detail.timeline().stream().map(event -> new OrderTimelineResponse(event.type(), event.occurredAt())).toList());
    }

    private static PriceBreakdownResponse priceBreakdown(PriceBreakdownDetails priceBreakdown) {
        return new PriceBreakdownResponse(
                priceBreakdown.productAmount(), priceBreakdown.shippingFee(), priceBreakdown.totalAmount());
    }
}
