package com.impati.commerce.payment.application;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.payment.adapter.out.persistence.InMemoryPaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PaymentService {
    private static final Set<String> DECLINE_TOKENS = Set.of("card_test_decline", "decline", "fail");

    private final InMemoryPaymentRepository payments;

    public PaymentService(InMemoryPaymentRepository payments) {
        this.payments = payments;
    }

    public PaymentResponse capture(String orderId, String memberId, Money amount, String paymentToken) {
        if (DECLINE_TOKENS.contains(paymentToken)) {
            throw DomainException.paymentDeclined("payment was declined by issuer");
        }
        var payment = new Payment(orderId, memberId, amount);
        payments.save(payment);
        return payment.toResponse();
    }
}

