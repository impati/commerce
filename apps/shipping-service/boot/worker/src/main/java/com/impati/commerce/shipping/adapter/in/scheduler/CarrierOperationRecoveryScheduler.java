package com.impati.commerce.shipping.adapter.in.scheduler;

import com.impati.commerce.shipping.application.port.in.CarrierOperationRecoveryUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CarrierOperationRecoveryScheduler {
    private final CarrierOperationRecoveryUseCase recovery;

    public CarrierOperationRecoveryScheduler(CarrierOperationRecoveryUseCase recovery) {
        this.recovery = recovery;
    }

    @Scheduled(fixedDelayString = "${shipping.carrier-operation-recovery-period-ms:10000}")
    void recover() {
        recovery.recoverPendingOperations(20);
    }
}
