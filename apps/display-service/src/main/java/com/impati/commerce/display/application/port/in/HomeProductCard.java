package com.impati.commerce.display.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;

import java.util.List;

/** 지면에 놓이는 상품 카드. 가격은 첫 판매 단위의 것이며 없을 수 있다. */
public record HomeProductCard(
        String productId,
        String name,
        String brand,
        String category,
        Money price,
        List<String> tags
) {
}
