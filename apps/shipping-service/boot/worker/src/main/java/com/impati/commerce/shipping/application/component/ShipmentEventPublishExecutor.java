package com.impati.commerce.shipping.application.component;

import com.impati.commerce.common.Ids;
import com.impati.commerce.shipping.application.port.in.ShipmentEventPublishUseCase;
import com.impati.commerce.shipping.application.port.out.ShipmentEventPublisher;
import com.impati.commerce.shipping.application.port.out.ShipmentEventRepository;
import com.impati.commerce.shipping.domain.ShipmentEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEventPublishExecutor implements ShipmentEventPublishUseCase {
    private static final int MAX_EVENTS_PER_SHIPMENT = 6;
    private final ShipmentEventRepository repository;
    private final ShipmentEventPublisher publisher;
    private final int batchSize;
    private final Duration retryDelay;
    private final int maxAttempts;

    public ShipmentEventPublishExecutor(ShipmentEventRepository repository, ShipmentEventPublisher publisher,
            @Value("${shipping.event-publish-shipments-per-cycle:3}") int batchSize,
            @Value("${shipping.event-publish-retry-delay:480s}") Duration retryDelay,
            @Value("${shipping.event-publish-max-attempts:5}") int maxAttempts,
            @Value("${commerce.kafka.send-timeout:17s}") Duration sendTimeout,
            @Value("${commerce.kafka.max-block:5s}") Duration maxBlock) {
        if (batchSize <= 0 || retryDelay.isZero() || retryDelay.isNegative() || maxAttempts <= 0) {
            throw new IllegalArgumentException("shipment event publisher settings must be positive");
        }
        var leaseNeeded = maxBlock.plus(sendTimeout)
                .multipliedBy((long) batchSize * MAX_EVENTS_PER_SHIPMENT);
        if (retryDelay.compareTo(leaseNeeded) < 0) {
            throw new IllegalArgumentException("shipping event retry delay must cover the whole batch");
        }
        this.repository = repository;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public int publishPending() {
        var claimed = repository.claimForPublish(Ids.newId("spub"), batchSize, retryDelay);
        var grouped = new LinkedHashMap<String, List<ShipmentEvent>>();
        claimed.forEach(event -> grouped.computeIfAbsent(event.shipmentId(), ignored -> new ArrayList<>()).add(event));
        var published = 0;
        for (var events : grouped.values()) {
            for (var event : events) {
                try {
                    publisher.publish(event);
                    event.markPublished();
                } catch (RuntimeException failure) {
                    event.markFailed(failure.getMessage(), maxAttempts);
                }
                repository.savePublishResult(event);
                if (!event.isPublished()) break;
                published++;
            }
        }
        return published;
    }
}
