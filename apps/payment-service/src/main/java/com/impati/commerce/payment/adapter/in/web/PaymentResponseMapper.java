package com.impati.commerce.payment.adapter.in.web;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.payment.application.port.in.AuthorizedPayment;
import com.impati.commerce.payment.application.port.in.CancelledPayment;
import com.impati.commerce.payment.application.port.in.CapturedPayment;
import com.impati.commerce.payment.application.port.in.PaymentDetails;
import com.impati.commerce.payment.application.port.in.RefundedPayment;

/**
 * 유스케이스 결과를 서비스 간 HTTP 계약으로 옮긴다.
 *
 * <p>이 변환이 어댑터에 있는 이유는 {@link PaymentResponse}가 <b>서비스 간</b> 계약이기
 * 때문이다. 응용 계층이 그것을 알면 인바운드 어댑터가 늘어날 때마다 응용이 바뀐다.
 *
 * <p><b>거래 식별자는 내보내지 않는다.</b> 대행사가 소유한 대사용 값이며 형제 서비스가 쓸 일이
 * 없다. 결과 타입에는 남아 있으므로 로깅이나 대사에는 그대로 쓸 수 있다.
 */
final class PaymentResponseMapper {
    private PaymentResponseMapper() {
    }

    static PaymentResponse from(AuthorizedPayment payment) {
        return response(payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status());
    }

    static PaymentResponse from(CapturedPayment payment) {
        return response(payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status());
    }

    static PaymentResponse from(CancelledPayment payment) {
        return response(payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status());
    }

    static PaymentResponse from(RefundedPayment payment) {
        return response(payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status());
    }

    static PaymentResponse from(PaymentDetails payment) {
        return response(payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status());
    }

    private static PaymentResponse response(
            String id, String orderId, String memberId, Money amount, String method, String status) {
        return new PaymentResponse(id, orderId, memberId, amount, method, status);
    }
}
