package com.impati.commerce.payment.application.port.out;

import com.impati.commerce.common.ApiContracts.Money;

/**
 * 결제 대행사 호출 포트. 구현은 {@code adapter/out/gateway}에 둔다.
 *
 * <p>대금을 실제로 움직이는 것은 대행사다. 이 서비스가 들고 있는 상태는 그 결과의 기록이며,
 * 상태 전이는 여기 있는 호출이 성공한 뒤에만 일어난다.
 *
 * <p><b>거절은 예외가 아니라 값이다.</b> 발급사가 거절하는 것은 시스템 오류가 아니라 정상적인
 * 응답이며 사유가 함께 온다 (PD-0011-R6). 예외로 표현하면 그 사유를 담을 자리가 없어진다.
 * 반대로 대행사에 닿지 못하거나 응답을 받지 못한 것은 실패이므로 예외로 올라온다.
 *
 * <p><b>승인 이후의 호출은 거래 식별자를 기준으로 멱등하다.</b> 같은 거래에 매입을 두 번
 * 요청하면 대행사가 같은 결과를 돌려준다. 그래서 응답을 받지 못한 호출자는 다시 부르는 것만으로
 * 결과를 확정할 수 있다 (ADR-0005).
 */
public interface PaymentGateway {
    /**
     * 대금을 확보한다. 청구는 확정되지 않는다.
     *
     * <p>거래 식별자와 결제 수단은 대행사가 정한다. 승인된 경우에만 그 값이 채워진다.
     */
    Authorization authorize(String orderId, Money amount, String paymentToken);

    /** 확보한 대금을 청구로 확정한다. */
    void capture(String transactionId);

    /** 확보만 하고 청구하지 않은 대금을 놓아준다. 사용자에게 흔적이 남지 않는다. */
    void cancel(String transactionId);

    /** 이미 청구한 대금을 되돌린다. 사용자 명세서에 흔적이 남는다. */
    void refund(String transactionId);

    /**
     * 승인 요청의 결과.
     *
     * <p>거절이면 {@code transactionId}와 {@code method}는 비어 있고 {@code declineReason}에
     * 사유가 담긴다. 승인이면 반대다.
     */
    record Authorization(boolean approved, String transactionId, String method, String declineReason) {
        public static Authorization approved(String transactionId, String method) {
            return new Authorization(true, transactionId, method, null);
        }

        public static Authorization declined(String reason) {
            return new Authorization(false, null, null, reason);
        }
    }
}
