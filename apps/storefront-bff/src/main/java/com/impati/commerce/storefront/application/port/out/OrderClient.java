package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import org.springframework.http.ResponseEntity;

public interface OrderClient {
    PurchaseQuoteResponse quote(String memberId, long cartVersion);
    ResponseEntity<CheckoutResponse> checkout(String memberId, String key, ConfirmedCheckoutRequest request);
}
