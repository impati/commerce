package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;

/**
 * 결제의 현재 상태 (PD-0011-R9).
 *
 * <p>넷은 "무엇을 했다"이고 이것만 "지금 이렇다"이다. 응답을 받지 못한 호출자가 결과를
 * 확정하는 경로이므로 상태 전체를 담는다.
 */
public record PaymentDetails(
        String id,
        String orderId,
        String memberId,
        Money amount,
        String method,
        String status,
        String transactionId
) {
}
