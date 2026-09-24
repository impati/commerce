package com.impati.commerce.shipping.application.port.out;

import com.impati.commerce.shipping.domain.ShipmentEvent;
import java.time.Duration;
import java.util.List;

public interface ShipmentEventRepository {
    void save(ShipmentEvent event);
    List<ShipmentEvent> claimForPublish(String publishId, int shipmentBatchSize, Duration retryDelay);
    void savePublishResult(ShipmentEvent event);
}
