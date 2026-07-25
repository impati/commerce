package com.impati.commerce.shipping.application;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.springframework.stereotype.Service;

@Service
public class ShippingService {
    private final ShipmentRepository shipments;

    public ShippingService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    public ShipmentResponse create(String orderId, String memberId, AddressResponse address) {
        var shipment = new Shipment(orderId, memberId, ShipmentMapper.toAddress(address));
        shipments.save(shipment);
        return ShipmentMapper.toResponse(shipment);
    }

    public ShipmentResponse get(String shipmentId) {
        return ShipmentMapper.toResponse(getShipment(shipmentId));
    }

    public ShipmentResponse ship(String shipmentId) {
        var shipment = getShipment(shipmentId);
        shipment.ship();
        shipments.save(shipment);
        return ShipmentMapper.toResponse(shipment);
    }

    public ShipmentResponse deliver(String shipmentId) {
        var shipment = getShipment(shipmentId);
        shipment.deliver();
        shipments.save(shipment);
        return ShipmentMapper.toResponse(shipment);
    }

    private Shipment getShipment(String shipmentId) {
        return shipments.findById(shipmentId)
                .orElseThrow(() -> DomainException.notFound("shipment not found"));
    }
}
