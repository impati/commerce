package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.in.CancellationRecoveryUseCase;
import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean({InventoryClient.class, PaymentClient.class, ShippingClient.class})
public class CancellationRecoveryExecutor implements CancellationRecoveryUseCase {
    public static final Duration LEASE_DURATION = Duration.ofMinutes(1);

    private final CancellationProgressRepository progressRepository;
    private final CancellationExecution execution;
    private final Clock clock;

    public CancellationRecoveryExecutor(
            CancellationProgressRepository progressRepository,
            CancellationExecution execution,
            Clock clock
    ) {
        this.progressRepository = progressRepository;
        this.execution = execution;
        this.clock = clock;
    }

    @Override
    public int recover(int batchSize) {
        var processed = 0;
        for (var orderId : progressRepository.findRecoverable(batchSize)) {
            var claimed = progressRepository.claim(orderId, LEASE_DURATION);
            if (claimed.isEmpty()) continue;
            execution.run(claimed.get());
            processed++;
        }
        return processed;
    }

    @Override
    public boolean retry(String orderId) {
        return progressRepository.requeue(orderId, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
