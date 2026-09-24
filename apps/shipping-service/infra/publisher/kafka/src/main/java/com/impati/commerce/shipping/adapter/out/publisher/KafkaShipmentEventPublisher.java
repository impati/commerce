package com.impati.commerce.shipping.adapter.out.publisher;

import com.impati.commerce.common.ApiContracts.ShipmentEventMessage;
import com.impati.commerce.shipping.application.port.out.ShipmentEventPublisher;
import com.impati.commerce.shipping.domain.ShipmentEvent;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaShipmentEventPublisher implements ShipmentEventPublisher {
    private final KafkaTemplate<String, ShipmentEventMessage> kafkaTemplate;
    private final String topic;
    private final Duration timeout;

    public KafkaShipmentEventPublisher(KafkaTemplate<String, ShipmentEventMessage> kafkaTemplate,
            String topic, Duration timeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.timeout = timeout;
    }

    @Override
    public void publish(ShipmentEvent event) {
        try {
            var message = new ShipmentEventMessage(event.id(), event.type(), event.shipmentId(), event.orderId(),
                    event.memberId(), event.occurredAt(), event.payload());
            kafkaTemplate.send(topic, event.orderId(), message).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("shipment event publish interrupted", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("shipment event publish failed: " + event.id(), failure);
        }
    }
}
