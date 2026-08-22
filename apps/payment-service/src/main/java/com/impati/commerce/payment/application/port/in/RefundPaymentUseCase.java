package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.PaymentResponse;

/**
 * 매입된 대금을 되돌린다 (PD-0011-R8).
 *
 * <p>승인 취소로 정리할 수 없는 경우에만 쓴다. 매입 결과를 확인하지 못한 체크아웃이 그것이다
 * (PD-0012-R12). 사용자 명세서에 청구와 환불 두 줄이 남으므로 정상 흐름에는 쓰지 않는다.
 */
public interface RefundPaymentUseCase {
    PaymentResponse refund(String paymentId);
}
