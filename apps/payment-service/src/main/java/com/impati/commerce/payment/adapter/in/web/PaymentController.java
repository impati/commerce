package com.impati.commerce.payment.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CapturePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.payment.application.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/capture")
    PaymentResponse capture(@RequestBody CapturePaymentRequest request) {
        return payments.capture(request.orderId(), request.memberId(), request.amount(), request.paymentToken());
    }
}

