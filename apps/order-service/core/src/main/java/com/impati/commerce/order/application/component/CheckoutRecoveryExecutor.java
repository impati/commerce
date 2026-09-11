package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.in.CheckoutRecoveryUseCase;
import com.impati.commerce.order.application.port.out.CartClient;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnBean({CartClient.class, InventoryClient.class, PaymentClient.class, ShippingClient.class})
public class CheckoutRecoveryExecutor implements CheckoutRecoveryUseCase {
    public static final Duration LEASE_DURATION = Duration.ofMinutes(1);

    private final CheckoutProgressRepository progressRepository;
    private final CheckoutExecution checkoutExecution;
    private final Clock clock;

    public CheckoutRecoveryExecutor(
            CheckoutProgressRepository progressRepository,
            CheckoutExecution checkoutExecution,
            Clock clock
    ) {
        this.progressRepository = progressRepository;
        this.checkoutExecution = checkoutExecution;
        this.clock = clock;
    }

    @Override
    public int recover(int batchSize) {
        var processed = 0;
        for (var orderId : progressRepository.findRecoverable(batchSize)) {
            var claimed = progressRepository.claim(orderId, LEASE_DURATION);
            if (claimed.isEmpty()) continue;
            checkoutExecution.run(claimed.get());
            processed++;
        }
        return processed;
    }

    @Override
    public boolean retry(String orderId) {
        return progressRepository.requeue(orderId,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
