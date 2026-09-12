package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutCartResponse;

/** cart-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface CartClient {
    CartResponse cart(String memberId);

    void clearCart(String memberId);

    CheckoutCartResponse checkout(String memberId, String orderId, long expectedVersion);
}
