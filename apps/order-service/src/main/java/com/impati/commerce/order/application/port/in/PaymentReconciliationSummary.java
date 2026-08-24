package com.impati.commerce.order.application.port.in;

/**
 * 한 번의 정리가 무엇을 했는지 (PD-0015-R1).
 *
 * <p>{@code candidates}와 {@code claimed}가 다르면 다른 인스턴스가 같은 주문을 먼저 집었다는
 * 뜻이고, {@code claimed}와 {@code resolved}가 다르면 정리가 실패해 표시가 남았다는 뜻이다
 * (PD-0015-R6). 셋을 함께 보아야 어느 쪽인지 구분된다.
 */
public record PaymentReconciliationSummary(int candidates, int claimed, int resolved) {
}
