package com.impati.commerce.order.adapter.in.scheduler;

import com.impati.commerce.order.application.port.in.CancellationRecoveryUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CancellationRecoveryScheduler {
    private final CancellationRecoveryUseCase recoveryUseCase;

    public CancellationRecoveryScheduler(CancellationRecoveryUseCase recoveryUseCase) {
        this.recoveryUseCase = recoveryUseCase;
    }

    @Scheduled(fixedDelayString = "${orders.cancellation-recovery-period-ms:10000}")
    public void recover() {
        recoveryUseCase.recover(50);
    }
}
