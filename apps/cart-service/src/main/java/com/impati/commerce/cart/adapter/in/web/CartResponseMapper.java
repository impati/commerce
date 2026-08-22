package com.impati.commerce.cart.adapter.in.web;

import com.impati.commerce.cart.application.port.in.CartDetails;
import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;

/**
 * 유스케이스 결과를 서비스 간 HTTP 계약으로 옮긴다.
 *
 * <p>이 변환이 어댑터에 있는 이유는 {@link CartResponse}가 <b>서비스 간</b> 계약이기 때문이다.
 * 응용 계층이 그것을 알면 인바운드 어댑터가 늘어날 때마다 응용이 바뀐다.
 */
final class CartResponseMapper {
    private CartResponseMapper() {
    }

    static CartResponse from(CartDetails cart) {
        return new CartResponse(
                cart.memberId(),
                cart.lines().stream().map(line -> new CartLineResponse(line.skuId(), line.quantity())).toList()
        );
    }
}
