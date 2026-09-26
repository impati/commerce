package com.impati.commerce.order.adapter.in.scheduler;

import com.impati.commerce.order.application.port.in.ReturnRecoveryUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReturnRecoveryScheduler {
    private final ReturnRecoveryUseCase returns;
    public ReturnRecoveryScheduler(ReturnRecoveryUseCase returns) { this.returns = returns; }

    @Scheduled(fixedDelayString = "${orders.return-recovery-interval:10000}",
            initialDelayString = "${orders.return-recovery-initial-delay:10000}")
    void recover() { returns.recover(50); }
}
