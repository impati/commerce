package com.impati.commerce.payment.application;

import com.impati.commerce.payment.domain.PaymentModels.Payment;

import java.util.Optional;

/**
 * 결제 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface PaymentRepository {
    void save(Payment payment);

    Optional<Payment> findById(String paymentId);
}
