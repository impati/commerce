package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.*;

public interface CartClient {
    CartResponse get(String memberId);
    CartResponse add(String memberId, CartItemRequest request);
}
