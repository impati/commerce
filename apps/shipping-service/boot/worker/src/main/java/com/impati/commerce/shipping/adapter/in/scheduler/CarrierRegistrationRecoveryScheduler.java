package com.impati.commerce.shipping.adapter.in.scheduler;

import com.impati.commerce.shipping.application.port.in.CarrierRegistrationRecoveryUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CarrierRegistrationRecoveryScheduler {
    private final CarrierRegistrationRecoveryUseCase recovery;

    public CarrierRegistrationRecoveryScheduler(CarrierRegistrationRecoveryUseCase recovery) {
        this.recovery = recovery;
    }

    @Scheduled(fixedDelayString = "${shipping.registration-recovery-period-ms:10000}")
    void recover() {
        recovery.recoverPendingRegistrations(20);
    }
}
