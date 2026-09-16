package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.*;

public interface OrderClient {
    PurchaseQuoteResponse quote(String memberId, long cartVersion);
    org.springframework.http.ResponseEntity<CheckoutResponse> checkout(String memberId, String key, ConfirmedCheckoutRequest request);
}
