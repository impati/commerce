package com.impati.commerce.payment.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CapturePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.payment.application.PaymentService;
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
    private final PaymentService payments;

    public InternalPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/capture")
    PaymentResponse capture(@RequestBody CapturePaymentRequest request) {
        return payments.capture(request.orderId(), request.memberId(), request.amount(), request.paymentToken());
    }
}
