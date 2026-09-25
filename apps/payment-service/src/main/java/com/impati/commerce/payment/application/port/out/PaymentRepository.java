package com.impati.commerce.payment.application.port.out;

import com.impati.commerce.payment.domain.PaymentModels.Payment;
import com.impati.commerce.payment.domain.PaymentModels.RefundOperation;

import java.util.List;

import java.util.Optional;

/**
 * 결제 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>넣기와 고치기를 나눈 이유는 "한 주문에 결제는 하나"(PD-0011-R2)를 저장소가 판정하기
 * 때문이다. 동시에 도착한 두 승인 요청은 둘 다 조회에서 못 찾으므로, 마지막 판정은 DB의
 * 유일 제약이 한다.
 */
public interface PaymentRepository {
    /** 새 결제를 넣는다. 같은 주문의 결제가 이미 있으면 아무것도 하지 않고 {@code false}를 돌려준다. */
    boolean insertIfAbsent(Payment payment);

    void update(Payment payment);

    Optional<Payment> findById(String paymentId);

    Optional<Payment> findByIdForUpdate(String paymentId);

    Optional<Payment> findByOrderId(String orderId);

    boolean insertRefundIfAbsent(RefundOperation refund);

    Optional<RefundOperation> findRefund(String returnId);

    Optional<RefundOperation> findRefundForUpdate(String returnId);

    List<RefundOperation> findPendingRefunds(int limit);

    void updateRefund(RefundOperation refund);
}
