package com.impati.commerce.payment.application.service;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.payment.application.port.in.PaymentUseCase;
import com.impati.commerce.payment.application.port.out.PaymentGateway;
import com.impati.commerce.payment.application.port.out.PaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link PaymentUseCase} 구현.
 *
 * <p>다섯 동작이 저장소와 대행사를 함께 쓰므로 한 클래스에 둔다 — 생성자 의존성을 메서드별
 * 사용으로 매핑해도 겹치지 않는 집합으로 갈리지 않는다.
 *
 * <p>계약은 포트에 있다. 여기에는 구현 사정만 적는다.
 */
@Service
public class PaymentService implements PaymentUseCase {
    private final PaymentRepository payments;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    @Override
    @Transactional
    public PaymentResponse authorize(String orderId, String memberId, Money amount, String paymentToken) {
        Optional<Payment> existing = payments.findByOrderId(orderId);
        if (existing.isPresent()) {
            return PaymentMapper.toResponse(existing.get());
        }

        var authorization = gateway.authorize(orderId, amount, paymentToken);
        if (!authorization.approved()) {
            throw DomainException.paymentDeclined("payment was declined by issuer: " + authorization.declineReason());
        }

        Payment payment = new Payment(orderId, memberId, amount, authorization.transactionId(), authorization.method());
        if (!payments.insertIfAbsent(payment)) {
            return PaymentMapper.toResponse(requireByOrder(orderId));
        }
        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse capture(String paymentId) {
        var payment = require(paymentId);
        if (payment.capture()) {
            gateway.capture(payment.transactionId());
            payments.update(payment);
        }
        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse cancel(String paymentId) {
        var payment = require(paymentId);
        if (payment.cancel()) {
            gateway.cancel(payment.transactionId());
            payments.update(payment);
        }
        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refund(String paymentId) {
        var payment = require(paymentId);
        if (payment.refund()) {
            gateway.refund(payment.transactionId());
            payments.update(payment);
        }
        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse get(String paymentId) {
        return PaymentMapper.toResponse(require(paymentId));
    }

    private Payment require(String paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> DomainException.notFound("payment not found"));
    }

    private Payment requireByOrder(String orderId) {
        return payments.findByOrderId(orderId)
                .orElseThrow(() -> DomainException.conflict("payment for order disappeared: " + orderId));
    }
}
