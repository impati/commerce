package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;

public interface CartClient {
    CartResponse get(String memberId);
    CartResponse add(String memberId, CartItemRequest request);
}
