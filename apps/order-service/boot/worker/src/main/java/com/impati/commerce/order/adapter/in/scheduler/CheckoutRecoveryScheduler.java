package com.impati.commerce.order.adapter.in.scheduler;

import com.impati.commerce.order.application.port.in.CheckoutRecoveryUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CheckoutRecoveryScheduler {
    private final CheckoutRecoveryUseCase checkoutRecoveryUseCase;

    public CheckoutRecoveryScheduler(CheckoutRecoveryUseCase checkoutRecoveryUseCase) {
        this.checkoutRecoveryUseCase = checkoutRecoveryUseCase;
    }

    @Scheduled(fixedDelayString = "${orders.checkout-recovery-period-ms:10000}")
    public void recover() {
        checkoutRecoveryUseCase.recover(50);
    }
}
