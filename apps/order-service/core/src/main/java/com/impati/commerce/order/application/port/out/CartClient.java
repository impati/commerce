package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.CartResponse;

/** cart-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface CartClient {
    CartResponse cart(String memberId);

    void clearCart(String memberId);
}
