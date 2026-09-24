package com.impati.commerce.shipping.domain;

import com.impati.commerce.common.Ids;
import java.time.OffsetDateTime;
import java.util.Map;

/** 배송 상태 변경과 같은 트랜잭션에 저장되는 발행 대기 사건. */
public final class ShipmentEvent {
    public enum PublishStatus { PENDING, PUBLISHED, FAILED }

    private final String id;
    private final String type;
    private final String shipmentId;
    private final String orderId;
    private final String memberId;
    private final OffsetDateTime occurredAt;
    private final Map<String, String> payload;
    private PublishStatus publishStatus;
    private int attempts;
    private String lastError;

    private ShipmentEvent(String id, String type, String shipmentId, String orderId, String memberId,
            OffsetDateTime occurredAt, Map<String, String> payload, PublishStatus publishStatus,
            int attempts, String lastError) {
        this.id = id;
        this.type = type;
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.memberId = memberId;
        this.occurredAt = occurredAt;
        this.payload = Map.copyOf(payload);
        this.publishStatus = publishStatus;
        this.attempts = attempts;
        this.lastError = lastError;
    }

    public static ShipmentEvent occurred(String type, ShippingModels.Shipment shipment,
            OffsetDateTime occurredAt) {
        return new ShipmentEvent(Ids.newId("sev"), type, shipment.id(), shipment.orderId(), shipment.memberId(),
                occurredAt, Map.of(
                        "shipmentStatus", shipment.status().name(),
                        "carrierCode", shipment.carrierCode(),
                        "carrierName", shipment.carrierName(),
                        "trackingNumber", shipment.trackingNumber()),
                PublishStatus.PENDING, 0, null);
    }

    public static ShipmentEvent restore(String id, String type, String shipmentId, String orderId, String memberId,
            OffsetDateTime occurredAt, Map<String, String> payload, PublishStatus publishStatus,
            int attempts, String lastError) {
        return new ShipmentEvent(id, type, shipmentId, orderId, memberId, occurredAt, payload,
                publishStatus, attempts, lastError);
    }

    public String id() { return id; }
    public String type() { return type; }
    public String shipmentId() { return shipmentId; }
    public String orderId() { return orderId; }
    public String memberId() { return memberId; }
    public OffsetDateTime occurredAt() { return occurredAt; }
    public Map<String, String> payload() { return payload; }
    public PublishStatus publishStatus() { return publishStatus; }
    public int attempts() { return attempts; }
    public String lastError() { return lastError; }
    public boolean isPublished() { return publishStatus == PublishStatus.PUBLISHED; }

    public void markPublished() {
        attempts++;
        publishStatus = PublishStatus.PUBLISHED;
        lastError = null;
    }

    public void markFailed(String error, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 400));
        publishStatus = attempts >= maxAttempts ? PublishStatus.FAILED : PublishStatus.PENDING;
    }
}
