package com.impati.commerce.payment.adapter.out.persistence;

import com.impati.commerce.payment.application.PaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {
    private final Map<String, Payment> payments = new ConcurrentHashMap<>();

    @Override
    public void save(Payment payment) {
        payments.put(payment.id(), payment);
    }

    @Override
    public Collection<Payment> findAll() {
        return payments.values();
    }
}
