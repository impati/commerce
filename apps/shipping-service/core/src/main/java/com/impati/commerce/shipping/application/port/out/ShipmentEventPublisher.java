package com.impati.commerce.shipping.application.port.out;

import com.impati.commerce.shipping.domain.ShipmentEvent;

public interface ShipmentEventPublisher {
    void publish(ShipmentEvent event);
}
