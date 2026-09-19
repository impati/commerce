package com.impati.commerce.order.application.model;

import com.impati.commerce.common.ApiContracts.Money;
import java.time.OffsetDateTime;

public record OrderSummary(String id, OffsetDateTime orderedAt, String representativeProductName,
        String representativeSkuName, int additionalProductCount, int totalQuantity,
        PriceBreakdownDetails priceBreakdown,
        String checkoutResult, String orderStatus, String cancellationStatus) {
    public Money total() {
        return priceBreakdown.totalAmount();
    }
}
