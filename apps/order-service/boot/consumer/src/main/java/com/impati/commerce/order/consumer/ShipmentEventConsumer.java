package com.impati.commerce.order.consumer;

import com.impati.commerce.common.ApiContracts.ShipmentEventMessage;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.ShipmentEventInbox;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShipmentEventConsumer {
    private final OrderRepository orders;
    private final OrderChanges orderChanges;
    private final ShipmentEventInbox inbox;

    public ShipmentEventConsumer(OrderRepository orders, OrderChanges orderChanges, ShipmentEventInbox inbox) {
        this.orders = orders;
        this.orderChanges = orderChanges;
        this.inbox = inbox;
    }

    @KafkaListener(topics = "${commerce.kafka.shipment-events-topic}")
    @Transactional
    public void consume(ShipmentEventMessage message) {
        if (!inbox.recordIfAbsent(message.eventId())) return;
        var order = orders.findById(message.orderId())
                .orElseThrow(() -> DomainException.notFound("order not found for shipment event"));
        if (!order.memberId().equals(message.memberId())) {
            throw DomainException.conflict("shipment event member does not match order");
        }
        order.applyShipmentEvent(message.type(), message.shipmentId(), message.payload(), message.occurredAt());
        orderChanges.commit(order);
    }
}
