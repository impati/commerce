package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.storefront.application.model.CheckoutSubmission;

public interface OrderClient {
    PurchaseQuoteResponse quote(String memberId, long cartVersion);

    CheckoutSubmission checkout(String memberId, String idempotencyKey, ConfirmedCheckoutRequest request);
}
