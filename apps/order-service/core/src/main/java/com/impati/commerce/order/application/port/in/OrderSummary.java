package com.impati.commerce.order.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;
import java.time.OffsetDateTime;

public record OrderSummary(String id, OffsetDateTime orderedAt, String representativeProductName,
        String representativeSkuName, int additionalProductCount, int totalQuantity, Money total,
        String checkoutResult, String orderStatus) {
}
