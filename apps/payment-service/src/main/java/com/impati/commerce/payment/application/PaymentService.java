package com.impati.commerce.payment.application;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class PaymentService {
    /**
     * 로컬 대역이 거절로 판정하는 토큰. 실제 대행사를 붙이면 이 판정은 어댑터로 옮겨진다
     * (BL-0032). 응용 계층이 이걸 아는 것은 지금의 한계이며 목표 상태가 아니다.
     */
    private static final Set<String> DECLINE_TOKENS = Set.of("card_test_decline", "decline", "fail");

    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    /**
     * 대금을 확보한다. 청구는 확정되지 않는다 (PD-0011-R1).
     *
     * <p>같은 주문의 승인이 이미 있으면 새로 만들지 않고 그것을 돌려준다 (PD-0011-R2).
     * 타임아웃은 요청이 실패했다는 뜻이 아니라 결과를 모른다는 뜻이므로 재요청이 정상이다.
     */
    @Transactional
    public PaymentResponse authorize(String orderId, String memberId, Money amount, String paymentToken) {
        var existing = payments.findByOrderId(orderId);
        if (existing.isPresent()) {
            return PaymentMapper.toResponse(existing.get());
        }
        if (DECLINE_TOKENS.contains(paymentToken)) {
            throw DomainException.paymentDeclined("payment was declined by issuer");
        }
        var payment = new Payment(orderId, memberId, amount);
        if (!payments.insertIfAbsent(payment)) {
            // 동시에 도착한 다른 요청이 먼저 만들었다. 결제는 주문당 하나다 (PD-0011-R2).
            return PaymentMapper.toResponse(requireByOrder(orderId));
        }
        return PaymentMapper.toResponse(payment);
    }

    /** 승인된 대금을 청구로 확정한다 (PD-0011-R1). */
    @Transactional
    public PaymentResponse capture(String paymentId) {
        var payment = require(paymentId);
        payment.capture();
        payments.update(payment);
        return PaymentMapper.toResponse(payment);
    }

    /** 승인을 취소한다. 사용자에게 흔적이 남지 않는다 (PD-0011-R3). */
    @Transactional
    public PaymentResponse cancel(String paymentId) {
        var payment = require(paymentId);
        payment.cancel();
        payments.update(payment);
        return PaymentMapper.toResponse(payment);
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
