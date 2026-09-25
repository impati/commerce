package com.impati.commerce.payment.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.RefundPaymentRequest;
import com.impati.commerce.common.ApiContracts.RefundPaymentResponse;
import com.impati.commerce.payment.application.port.in.PaymentUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게이트웨이가 노출하지 않는 경로. 형제 서비스만 부른다 (ADR-0003).
 *
 * <p>이 서비스의 경로는 전부 내부 등급이다. 결제 승인은 order-service의 checkout saga가 부르며
 * 요청 본문에 {@code memberId}와 금액이 들어간다. 사용자에게 열면 금액과 신원을 임의로 넣어
 * 승인을 만들 수 있다.

 */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {
    private final PaymentUseCase paymentUseCase;

    public InternalPaymentController(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @PostMapping("/authorize")
    PaymentResponse authorize(@RequestBody AuthorizePaymentRequest request) {
        return PaymentResponseMapper.from(paymentUseCase.authorize(
                request.orderId(), request.memberId(), request.amount(), request.paymentToken()));
    }

    @PostMapping("/{paymentId}/capture")
    PaymentResponse capture(@PathVariable String paymentId) {
        return PaymentResponseMapper.from(paymentUseCase.capture(paymentId));
    }

    @PostMapping("/{paymentId}/cancel")
    PaymentResponse cancel(@PathVariable String paymentId) {
        return PaymentResponseMapper.from(paymentUseCase.cancel(paymentId));
    }

    @PostMapping("/{paymentId}/refund")
    PaymentResponse refund(@PathVariable String paymentId) {
        return PaymentResponseMapper.from(paymentUseCase.refund(paymentId));
    }

    @PostMapping("/{paymentId}/refunds")
    RefundPaymentResponse refundForReturn(
            @PathVariable String paymentId,
            @RequestBody RefundPaymentRequest request
    ) {
        var refund = paymentUseCase.refundForReturn(paymentId, request.returnId(), request.amount());
        return new RefundPaymentResponse(refund.returnId(), refund.paymentId(), refund.amount(), refund.status());
    }

    @GetMapping("/refunds/{returnId}")
    RefundPaymentResponse getReturnRefund(@PathVariable String returnId) {
        var refund = paymentUseCase.getReturnRefund(returnId);
        return new RefundPaymentResponse(refund.returnId(), refund.paymentId(), refund.amount(), refund.status());
    }

    @GetMapping("/{paymentId}")
    PaymentResponse get(@PathVariable String paymentId) {
        return PaymentResponseMapper.from(paymentUseCase.get(paymentId));
    }

    @GetMapping("/orders/{orderId}")
    PaymentResponse getForOrder(@PathVariable String orderId) {
        return PaymentResponseMapper.from(paymentUseCase.getForOrder(orderId));
    }
}
