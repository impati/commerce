package com.impati.commerce.order.application;

import com.impati.commerce.common.ApiContracts.CapturePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;

/**
 * payment-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다.
 *
 * <p>결제 거절은 HTTP 상태가 아니라 {@code DomainException.paymentDeclined}로 올라온다.
 * 어댑터가 프로토콜 오류를 도메인 언어로 옮긴다.
 */
public interface PaymentClient {
    PaymentResponse capturePayment(CapturePaymentRequest request);
}
