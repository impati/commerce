package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;

/**
 * 대금이 확보됐다 (PD-0011-R1). 청구는 아직 확정되지 않았다.
 *
 * <p>동작마다 결과 타입이 다르다. 지금은 필드가 같지만 갈릴 이유가 서로 다르므로 하나로 묶지
 * 않는다 — 공용 타입이면 한 동작에만 필요한 필드가 나머지 넷에도 따라다닌다.
 *
 * <p>{@code transactionId}는 대행사 거래 식별자다. 대사에 쓰이는 내부 값이므로 바깥으로
 * 내보낼지는 어댑터가 정한다.
 */
public record AuthorizedPayment(
        String id,
        String orderId,
        String memberId,
        Money amount,
        String method,
        String status,
        String transactionId
) {
}
