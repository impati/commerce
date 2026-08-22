package com.impati.commerce.payment.application.component;

import com.impati.commerce.payment.application.port.in.AuthorizedPayment;
import com.impati.commerce.payment.application.port.in.CancelledPayment;
import com.impati.commerce.payment.application.port.in.CapturedPayment;
import com.impati.commerce.payment.application.port.in.PaymentDetails;
import com.impati.commerce.payment.application.port.in.RefundedPayment;
import com.impati.commerce.payment.domain.PaymentModels.Payment;

/**
 * 도메인 모델을 유스케이스 결과로 옮긴다. 도메인은 결과 타입을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 이걸 볼 수 있으면 도메인 객체를 손에 넣어야
 * 부를 수 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 *
 * <p>바깥 표현(HTTP 등)으로 옮기는 것은 각 인바운드 어댑터의 일이다. 여기서 하면 응용 계층이
 * 어댑터의 표현을 알게 되어 결과 타입을 따로 둔 의미가 사라진다.
 */
final class PaymentMapper {
    private PaymentMapper() {
    }

    static AuthorizedPayment toAuthorized(Payment payment) {
        return new AuthorizedPayment(
                payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status(), payment.transactionId());
    }

    static CapturedPayment toCaptured(Payment payment) {
        return new CapturedPayment(
                payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status(), payment.transactionId());
    }

    static CancelledPayment toCancelled(Payment payment) {
        return new CancelledPayment(
                payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status(), payment.transactionId());
    }

    static RefundedPayment toRefunded(Payment payment) {
        return new RefundedPayment(
                payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status(), payment.transactionId());
    }

    static PaymentDetails toDetails(Payment payment) {
        return new PaymentDetails(
                payment.id(), payment.orderId(), payment.memberId(),
                payment.amount(), payment.method(), payment.status(), payment.transactionId());
    }
}
