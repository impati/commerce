package com.impati.commerce.cart.application;

import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다. 도메인은 계약을 모른다.
 */
final class CartMapper {
    private CartMapper() {
    }

    static CartResponse toResponse(Cart cart) {
        return new CartResponse(
                cart.memberId(),
                cart.lines().stream()
                        .map(line -> new CartLineResponse(line.skuId(), line.quantity()))
                        .toList()
        );
    }
}
