package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.PaymentResponse;

/**
 * 확보한 대금을 청구로 확정한다 (PD-0011-R1).
 *
 * <p>여러 번 도착해도 첫 결과를 유지한다 (PD-0011-R4). 되돌리는 경로에서 호출되고 그 경로가
 * 재시도될 수 있으므로 같은 요청이 두 번 오는 것이 정상이다.
 */
public interface CapturePaymentUseCase {
    PaymentResponse capture(String paymentId);
}
