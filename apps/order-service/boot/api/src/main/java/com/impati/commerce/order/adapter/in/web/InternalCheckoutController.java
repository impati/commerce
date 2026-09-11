package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.in.CheckoutRecoveryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalCheckoutController {
    private static final Logger log = LoggerFactory.getLogger(InternalCheckoutController.class);
    private final CheckoutRecoveryUseCase checkoutRecoveryUseCase;

    public InternalCheckoutController(CheckoutRecoveryUseCase checkoutRecoveryUseCase) {
        this.checkoutRecoveryUseCase = checkoutRecoveryUseCase;
    }

    @PostMapping("/internal/orders/{orderId}/checkout/retry")
    ResponseEntity<Void> retry(
            @PathVariable String orderId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "system") String actor
    ) {
        var queued = checkoutRecoveryUseCase.retry(orderId);
        log.info("checkout_manual_retry actor={} order={} result={}", actor, orderId, queued ? "QUEUED" : "REJECTED");
        if (!queued) throw DomainException.conflict("checkout cannot be retried from current state");
        return ResponseEntity.accepted().build();
    }
}
