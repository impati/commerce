package com.impati.commerce.payment.application.service;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.payment.application.port.out.PaymentGateway;
import com.impati.commerce.payment.application.port.out.PaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 결제 유스케이스.
 *
 * <p>다섯 동작이 저장소와 대행사를 함께 쓰므로 한 클래스에 둔다 — 생성자 의존성을 메서드별
 * 사용으로 매핑해도 겹치지 않는 집합으로 갈리지 않는다.
 */
@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    /**
     * 대금을 확보한다. 청구는 확정되지 않는다 (PD-0011-R1).
     *
     * <p>같은 주문의 승인이 이미 있으면 새로 만들지 않고 그것을 돌려준다 (PD-0011-R2).
     * 타임아웃은 요청이 실패했다는 뜻이 아니라 결과를 모른다는 뜻이므로 재요청이 정상이다.
     */
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

    /** 승인된 대금을 청구로 확정한다 (PD-0011-R1). 여러 번 도착해도 첫 결과다 (PD-0011-R4). */
    @Transactional
    public PaymentResponse capture(String paymentId) {
        var payment = require(paymentId);
        if (payment.capture()) {
            gateway.capture(payment.transactionId());
            payments.update(payment);
        }
        return PaymentMapper.toResponse(payment);
    }

    /** 승인을 취소한다. 사용자에게 흔적이 남지 않는다 (PD-0011-R3). */
    @Transactional
    public PaymentResponse cancel(String paymentId) {
        var payment = require(paymentId);
        if (payment.cancel()) {
            gateway.cancel(payment.transactionId());
            payments.update(payment);
        }
        return PaymentMapper.toResponse(payment);
    }

    /**
     * 매입된 대금을 되돌린다 (PD-0011-R8).
     *
     * <p>승인 취소로 정리할 수 없는 경우에만 쓴다. 매입 결과를 확인하지 못한 체크아웃이
     * 그것이다 — 되돌리려는 시점에 결제가 이미 매입돼 있으면 취소가 통하지 않는다.
     */
    @Transactional
    public PaymentResponse refund(String paymentId) {
        var payment = require(paymentId);
        if (payment.refund()) {
            gateway.refund(payment.transactionId());
            payments.update(payment);
        }
        return PaymentMapper.toResponse(payment);
    }

    /** 매입 여부를 확인하는 경로 (PD-0011-R9). 결과를 받지 못한 호출자가 나중에 다시 묻는다. */
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
