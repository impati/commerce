package com.impati.commerce.catalog.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;

import java.util.Map;

/** 판매 단위의 현재 상태. */
public record SkuDetails(
        String id,
        String productId,
        String name,
        Money price,
        Map<String, String> attributes,
        String status
) {
}
