package com.impati.commerce.storefront.application.port.in;

import com.impati.commerce.storefront.application.model.CartPage;

public interface CartPageUseCase {

    CartPage get(String memberId);
}
