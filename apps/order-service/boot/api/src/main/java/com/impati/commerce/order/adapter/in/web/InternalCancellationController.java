package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.in.CancellationRecoveryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalCancellationController {
    private static final Logger log = LoggerFactory.getLogger(InternalCancellationController.class);
    private final CancellationRecoveryUseCase recoveryUseCase;

    public InternalCancellationController(CancellationRecoveryUseCase recoveryUseCase) {
        this.recoveryUseCase = recoveryUseCase;
    }

    @PostMapping("/internal/orders/{orderId}/cancellation/retry")
    ResponseEntity<Void> retry(
            @PathVariable String orderId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "system") String actor
    ) {
        var queued = recoveryUseCase.retry(orderId);
        log.info("cancellation_manual_retry actor={} order={} result={}",
                actor, orderId, queued ? "QUEUED" : "REJECTED");
        if (!queued) throw DomainException.conflict("cancellation cannot be retried from current state");
        return ResponseEntity.accepted().build();
    }
}
