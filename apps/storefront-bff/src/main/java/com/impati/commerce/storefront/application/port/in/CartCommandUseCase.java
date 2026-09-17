package com.impati.commerce.storefront.application.port.in;

import com.impati.commerce.storefront.application.model.CartState;

public interface CartCommandUseCase {
    CartState addItem(String memberId, String skuId, int quantity);
}
