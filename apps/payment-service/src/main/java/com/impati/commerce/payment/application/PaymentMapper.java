package com.impati.commerce.payment.application;

import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.payment.domain.PaymentModels.Payment;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다. 도메인은 계약을 모른다.
 */
final class PaymentMapper {
    private PaymentMapper() {
    }

    static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.orderId(),
                payment.memberId(),
                payment.amount(),
                payment.method(),
                payment.status(),
                payment.transactionId()
        );
    }
}
