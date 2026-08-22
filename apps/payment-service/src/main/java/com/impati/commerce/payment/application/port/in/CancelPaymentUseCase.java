package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.PaymentResponse;

/**
 * 승인을 취소한다. 사용자에게 흔적이 남지 않는다 (PD-0011-R3).
 *
 * <p>매입된 결제에는 통하지 않는다. 그때 되돌리는 수단은 환불이다
 * ({@link RefundPaymentUseCase}).
 */
public interface CancelPaymentUseCase {
    PaymentResponse cancel(String paymentId);
}
