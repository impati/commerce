package com.impati.commerce.order.consumer;

import com.impati.commerce.common.ApiContracts.ShipmentEventMessage;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.ReturnProgressRepository;
import com.impati.commerce.order.domain.ReturnProgress;
import org.springframework.stereotype.Component;

@Component
public class ReturnShipmentEventHandler {
    private final ReturnProgressRepository returns;
    private final OrderRepository orders;
    private final OrderChanges changes;

    public ReturnShipmentEventHandler(ReturnProgressRepository returns, OrderRepository orders, OrderChanges changes) {
        this.returns = returns; this.orders = orders; this.changes = changes;
    }

    public void handle(ShipmentEventMessage message) {
        var returnId = message.payload().get("returnId");
        var progress = returns.findByIdForUpdate(returnId)
                .orElseThrow(() -> DomainException.notFound("return not found for shipment event"));
        if (!progress.orderId().equals(message.orderId()) || !progress.memberId().equals(message.memberId())) {
            throw DomainException.conflict("return shipment event does not match return");
        }
        var order = orders.findById(message.orderId()).orElseThrow(() -> DomainException.notFound("order not found"));
        switch (message.type()) {
            case "RETURN_PICKUP_SCHEDULED" -> progress.pickupScheduled(message.shipmentId());
            case "RETURN_PICKED_UP" -> {
                var first = progress.refundStatus() == ReturnProgress.WorkStatus.NOT_READY;
                progress.pickedUp();
                if (first) order.recordReturnRefundStarted(progress.id());
            }
            case "RETURN_IN_TRANSIT" -> progress.pickedUp();
            case "RETURN_RECEIVED" -> {
                var first = progress.refundStatus() == ReturnProgress.WorkStatus.NOT_READY;
                progress.received(message.occurredAt());
                if (first) order.recordReturnRefundStarted(progress.id());
            }
            case "RETURN_PICKUP_FAILED" -> progress.pickupFailed();
            case "RETURN_CONFLICT" -> { progress.attention(); order.recordReturnAttention(progress.id()); }
            default -> throw DomainException.validation("unknown return shipment event type");
        }
        returns.save(progress);
        changes.commit(order);
    }

    public void handleFailedDelivery(ShipmentEventMessage message) {
        var order = orders.findById(message.orderId()).orElseThrow(() -> DomainException.notFound("order not found"));
        var returnId = "ret_auto_" + order.id();
        var progress = ReturnProgress.failedDelivery(returnId, order.id(), order.memberId(), order.total(),
                order.shippingAddress(), message.occurredAt());
        if (returns.insertIfAbsent(progress)) {
            order.recordReturnAccepted(returnId, "FAILED_DELIVERY");
            order.recordReturnRefundStarted(returnId);
            changes.commit(order);
        }
    }
}
