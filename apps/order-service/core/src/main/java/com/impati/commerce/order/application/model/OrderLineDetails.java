package com.impati.commerce.order.application.model;

import com.impati.commerce.common.ApiContracts.Money;

/** 주문 한 줄. 단가는 주문 시점의 값으로 확정된다 (PD-0023-R5, R10). */
public record OrderLineDetails(
        String skuId,
        String productId,
        String productName,
        String skuName,
        int quantity,
        Money unitPrice,
        Money lineTotal
) {
}
