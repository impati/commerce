package com.impati.commerce.shipping.application.port.out;

import java.time.OffsetDateTime;

public record CarrierEventRecord(
        String eventId,
        String shipmentId,
        String carrierCode,
        String trackingNumber,
        String eventType,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt,
        OffsetDateTime lastReceivedAt,
        String processingResult,
        String shipmentStatus,
        int duplicateCount
) { }
