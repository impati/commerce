package com.impati.commerce.cart.application.component;

import com.impati.commerce.cart.application.port.in.CartDetails;
import com.impati.commerce.cart.application.port.in.CartLine;
import com.impati.commerce.cart.domain.CartModels.Cart;

/**
 * 도메인 모델을 유스케이스 결과로 옮긴다. 도메인은 결과 타입을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 볼 수 있으면 도메인 객체를 손에 넣어야 부를 수
 * 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 */
final class CartMapper {
    private CartMapper() {
    }

    static CartDetails toDetails(Cart cart) {
        return new CartDetails(
                cart.memberId(),
                cart.lines().stream().map(line -> new CartLine(line.skuId(), line.quantity())).toList()
        );
    }
}
