package com.impati.commerce.order.application.port.in;

import com.impati.commerce.order.application.model.CheckoutResult;
import com.impati.commerce.order.application.model.PurchaseQuoteDetails;
import com.impati.commerce.order.domain.IdempotencyKey;

/**
 * 주문으로 할 수 있는 일.
 */
public interface OrderUseCase {

    /**
     * 저장하지 않고 현재 장바구니와 상품 가격으로 견적을 계산한다 (PD-0021-R1).
     */
    PurchaseQuoteDetails quote(String memberId, long expectedVersion);

    /**
     * 확인한 구매 내용과 금액이 현재 상태와 일치할 때 접수한다 (PD-0021-R2, R3).
     */
    CheckoutResult checkoutConfirmed(
            String memberId,
            IdempotencyKey key,
            String paymentToken,
            String addressId,
            String quoteId,
            String addressConfirmationToken
    );

    /**
     * 주문 식별자로 폴링할 체크아웃 결과를 돌려준다.
     */
    CheckoutResult getCheckoutResultOwned(String memberId, String orderId);

}
