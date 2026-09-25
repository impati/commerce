package com.impati.commerce.payment.adapter.in.scheduler;

import com.impati.commerce.payment.application.port.in.PaymentUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 결과 불명 상태로 남은 반품 환불을 같은 업무 키로 조회·재시도한다. */
@Component
public class RefundRecoveryWorker {
    private final PaymentUseCase payments;

    public RefundRecoveryWorker(PaymentUseCase payments) {
        this.payments = payments;
    }

    @Scheduled(
            fixedDelayString = "${payment.refund-recovery-interval:10000}",
            initialDelayString = "${payment.refund-recovery-initial-delay:10000}"
    )
    void recover() {
        payments.recoverPendingRefunds(50);
    }
}
