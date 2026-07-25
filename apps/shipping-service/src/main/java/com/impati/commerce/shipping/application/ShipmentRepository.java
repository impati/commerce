package com.impati.commerce.shipping.application;

import com.impati.commerce.shipping.domain.ShippingModels.Shipment;

import java.util.Collection;
import java.util.Optional;

/**
 * 배송 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface ShipmentRepository {
    void save(Shipment shipment);

    Optional<Shipment> findById(String shipmentId);

    Collection<Shipment> findAll();
}
