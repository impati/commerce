package com.impati.commerce.order.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderHistoryDetail(String id, OffsetDateTime orderedAt, String checkoutResult,
        String orderStatus, List<OrderLineDetails> lines, Money total, OrderAddress shippingAddress,
        String trackingNumber, List<OrderTimelineEntry> timeline) {
}
