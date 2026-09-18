package com.impati.commerce.storefront.application.component;

import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.storefront.application.model.CheckoutSubmission;
import com.impati.commerce.storefront.application.port.in.PurchaseUseCase;
import com.impati.commerce.storefront.application.port.out.OrderClient;
import org.springframework.stereotype.Component;

@Component
public class PurchaseExecutor implements PurchaseUseCase {
    private final OrderClient orderClient;

    public PurchaseExecutor(OrderClient orderClient) {
        this.orderClient = orderClient;
    }

    @Override
    public CheckoutSubmission checkout(
            String memberId,
            String idempotencyKey,
            String paymentToken,
            String addressId,
            String quoteId,
            String addressConfirmationToken
    ) {
        var request = new ConfirmedCheckoutRequest(paymentToken, addressId, quoteId, addressConfirmationToken);
        return orderClient.checkout(memberId, idempotencyKey, request);
    }
}
