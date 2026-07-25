package com.impati.commerce.shipping.adapter.out.persistence;

import com.impati.commerce.shipping.application.ShipmentRepository;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryShipmentRepository implements ShipmentRepository {
    private final Map<String, Shipment> shipments = new ConcurrentHashMap<>();

    @Override
    public void save(Shipment shipment) {
        shipments.put(shipment.id(), shipment);
    }

    @Override
    public Optional<Shipment> findById(String shipmentId) {
        return Optional.ofNullable(shipments.get(shipmentId));
    }

    @Override
    public Collection<Shipment> findAll() {
        return shipments.values();
    }
}
