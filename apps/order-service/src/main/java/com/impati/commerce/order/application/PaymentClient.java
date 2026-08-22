package com.impati.commerce.order.application;

import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;

/**
 * payment-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다.
 *
 * <p>결제 거절은 HTTP 상태가 아니라 {@code DomainException.paymentDeclined}로 올라온다.
 * 어댑터가 프로토콜 오류를 도메인 언어로 옮긴다.
 *
 * <p>승인과 매입이 나뉘어 있다 (PD-0011-R1). 승인은 대금을 확보만 하므로 취소해도 사용자에게
 * 흔적이 남지 않고, 매입은 청구를 확정하므로 되돌릴 수 없다.
 */
public interface PaymentClient {
    PaymentResponse authorizePayment(AuthorizePaymentRequest request);

    PaymentResponse capturePayment(String paymentId);

    PaymentResponse cancelPayment(String paymentId);
}
