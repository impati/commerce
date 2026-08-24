package com.impati.commerce.order.application.port.in;

/**
 * 결제 미확인으로 표시된 주문을 정리한다 (PD-0015).
 *
 * <p>체크아웃이 매입 결과를 두 번 모두 확인하지 못하면 주문을 되돌리고 표시한다
 * (PD-0012-R12). 표시된 주문은 고객의 대금이 나간 채로 있을 수 있으므로, 결제에 매입 여부를
 * 물어 필요한 조치를 하고 표시를 해제하는 것이 이 유스케이스다.
 */
public interface PaymentReconciliationUseCase {
    PaymentReconciliationSummary reconcileUnknownPaymentOutcomes();
}
