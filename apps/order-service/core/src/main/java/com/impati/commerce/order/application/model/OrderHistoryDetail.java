package com.impati.commerce.order.application.model;

import com.impati.commerce.common.ApiContracts.Money;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderHistoryDetail(String id, OffsetDateTime orderedAt, String checkoutResult,
        String orderStatus, List<OrderLineDetails> lines, PriceBreakdownDetails priceBreakdown,
        OrderAddress shippingAddress,
        String trackingNumber, List<OrderTimelineEntry> timeline) {
    public Money total() {
        return priceBreakdown.totalAmount();
    }
}
