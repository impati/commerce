package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.ChangeCartQuantityRequest;

public interface CartClient {
    CartResponse get(String memberId);

    CartResponse add(String memberId, CartItemRequest request);

    CartResponse changeQuantity(String memberId, String skuId, ChangeCartQuantityRequest request);

    CartResponse remove(String memberId, String skuId, long expectedVersion);
}
