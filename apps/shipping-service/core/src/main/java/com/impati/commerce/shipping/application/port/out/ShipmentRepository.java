package com.impati.commerce.shipping.application.port.out;

import com.impati.commerce.shipping.domain.ShippingModels.Shipment;

import java.util.Collection;
import java.util.Optional;
import java.time.OffsetDateTime;

/**
 * 배송 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface ShipmentRepository {
    boolean insertIfAbsent(Shipment shipment);

    void save(Shipment shipment);

    Optional<Shipment> findById(String shipmentId);

    Optional<Shipment> findByIdForUpdate(String shipmentId);

    Optional<Shipment> findByOrderId(String orderId);

    Optional<Shipment> findByCarrierAndTrackingForUpdate(String carrierCode, String trackingNumber);

    Collection<Shipment> findAll();

    boolean insertCarrierEventIfAbsent(CarrierEventRecord event);

    Optional<CarrierEventRecord> findCarrierEvent(String eventId);

    void completeCarrierEvent(String eventId, String result, String shipmentStatus);

    void recordDuplicateCarrierEvent(String eventId, OffsetDateTime receivedAt);
}
