package com.impati.commerce.storefront.application.model;

import com.impati.commerce.common.ApiContracts.Money;
import java.util.List;

public record CartPage(
        String memberId,
        long version,
        List<Line> lines,
        Quote quote,
        List<String> unavailable,
        boolean checkoutAllowed
) {
    public record Line(
            String skuId,
            int quantity,
            String productName,
            String skuName,
            Integer availableQuantity,
            boolean informationAvailable
    ) {
    }

    public record Quote(
            String id,
            long cartVersion,
            List<PriceLine> lines,
            Money total
    ) {
    }

    public record PriceLine(
            String skuId,
            int quantity,
            Money unitPrice,
            Money lineTotal
    ) {
    }
}
