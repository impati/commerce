package com.impati.commerce.storefront.application.port.in;

import com.impati.commerce.storefront.application.model.CheckoutSubmission;

public interface PurchaseUseCase {
    CheckoutSubmission checkout(
            String memberId,
            String idempotencyKey,
            String paymentToken,
            String addressId,
            String quoteId,
            String addressConfirmationToken
    );
}
