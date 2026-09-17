package com.impati.commerce.order.application.model;

import com.impati.commerce.common.ApiContracts.Money;
import java.util.List;

public record PurchaseQuoteDetails(
        String id,
        long cartVersion,
        List<Line> lines,
        Money total
) {
    public record Line(
            String skuId,
            int quantity,
            Money unitPrice,
            Money lineTotal
    ) {
    }
}
