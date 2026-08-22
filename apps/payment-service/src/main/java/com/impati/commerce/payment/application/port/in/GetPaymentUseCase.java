package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.PaymentResponse;

/**
 * 결제의 현재 상태를 돌려준다 (PD-0011-R9).
 *
 * <p>응답을 받지 못한 호출자가 결과를 확정하는 경로다. 이것이 없으면 모르는 상태를 영원히
 * 확정할 수 없다.
 */
public interface GetPaymentUseCase {
    PaymentResponse get(String paymentId);
}
