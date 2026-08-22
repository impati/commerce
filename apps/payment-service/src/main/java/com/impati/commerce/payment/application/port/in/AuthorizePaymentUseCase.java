package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;

/**
 * 대금을 확보한다. 청구는 확정되지 않는다 (PD-0011-R1).
 *
 * <p>같은 주문의 승인이 이미 있으면 새로 만들지 않고 그것을 돌려준다 (PD-0011-R2). 타임아웃은
 * 요청이 실패했다는 뜻이 아니라 결과를 모른다는 뜻이므로 재요청이 정상이다.
 *
 * <p>발급사가 거절하면 {@code DomainException.paymentDeclined}가 올라온다. 거절은 시스템
 * 오류와 구분되는 결과다 (PD-0011-R6).
 */
public interface AuthorizePaymentUseCase {
    PaymentResponse authorize(String orderId, String memberId, Money amount, String paymentToken);
}
