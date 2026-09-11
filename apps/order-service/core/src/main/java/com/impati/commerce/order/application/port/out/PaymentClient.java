package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import java.util.Optional;

/**
 * payment-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다.
 *
 * <p>결제 거절은 HTTP 상태가 아니라 {@code DomainException.paymentDeclined}로 올라온다.
 * 어댑터가 프로토콜 오류를 도메인 언어로 옮긴다.
 *
 * <p>승인과 매입이 나뉘어 있다 (PD-0011-R1). 승인은 대금을 확보만 하므로 취소해도 사용자에게
 * 흔적이 남지 않고, 매입은 청구를 확정하므로 취소할 수 없다 — 되돌리는 수단은 환불뿐이다
 * (PD-0011-R8).
 */
public interface PaymentClient {
    PaymentResponse authorizePayment(AuthorizePaymentRequest request);

    PaymentResponse capturePayment(String paymentId);

    PaymentResponse cancelPayment(String paymentId);

    /**
     * 매입된 대금을 되돌린다 (PD-0011-R8).
     *
     * <p>고객 명세서에 청구와 환불 두 줄이 남으므로 정상 흐름에서는 쓰지 않는다. 매입 결과를
     * 확인하지 못한 채 되돌린 주문을 정리할 때만 쓴다 (PD-0017-R6).
     */
    PaymentResponse refundPayment(String paymentId);

    /**
     * 결제의 현재 상태를 묻는다 (PD-0011-R9).
     *
     * <p>부수효과가 없으므로 실패해도 아무 일도 일어나지 않는다. 매입 여부를 모르는 상태를
     * 확정하는 유일한 수단이며, 이것 없이 조치하면 매입되지 않은 결제를 환불하려 하거나
     * 매입된 결제를 취소하려 한다.
     */
    PaymentResponse payment(String paymentId);

    Optional<PaymentResponse> paymentForOrder(String orderId);
}
