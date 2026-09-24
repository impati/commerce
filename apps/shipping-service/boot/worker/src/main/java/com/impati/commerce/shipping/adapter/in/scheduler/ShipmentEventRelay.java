package com.impati.commerce.shipping.adapter.in.scheduler;

import com.impati.commerce.shipping.application.port.in.ShipmentEventPublishUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEventRelay {
    private final ShipmentEventPublishUseCase useCase;

    public ShipmentEventRelay(ShipmentEventPublishUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${shipping.event-publish-interval:1000}")
    void publish() {
        useCase.publishPending();
    }
}
