package com.impati.commerce.shipping.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import com.impati.commerce.shipping.application.port.out.ShipmentRepository;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.springframework.stereotype.Component;

@Component
public class ShippingExecutor implements ShippingUseCase {
    private final ShipmentRepository shipments;

    public ShippingExecutor(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    public ShipmentDetails create(String orderId, String memberId, ShipmentAddress address) {
        var shipment = new Shipment(orderId, memberId, ShipmentMapper.toAddress(address));
        shipments.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    @Override
    public ShipmentDetails get(String shipmentId) {
        return ShipmentMapper.toDetails(getShipment(shipmentId));
    }

    @Override
    public ShipmentDetails ship(String shipmentId) {
        var shipment = getShipment(shipmentId);
        shipment.ship();
        shipments.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    /** 아직 나가지 않은 배송을 없앤다 (PD-0013-R5). 체크아웃 보상이 부른다 (PD-0012-R6). */
    @Override
    public ShipmentDetails cancel(String shipmentId) {
        var shipment = getShipment(shipmentId);
        shipment.cancel();
        shipments.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    @Override
    public ShipmentDetails deliver(String shipmentId) {
        var shipment = getShipment(shipmentId);
        shipment.deliver();
        shipments.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    private Shipment getShipment(String shipmentId) {
        return shipments.findById(shipmentId)
                .orElseThrow(() -> DomainException.notFound("shipment not found"));
    }
}
