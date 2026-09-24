package com.impati.commerce.shipping.application.port.in;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.domain.ShippingModels.CarrierEventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public record CarrierEventCommand(
        String eventId,
        String carrierCode,
        String trackingNumber,
        CarrierEventType type,
        OffsetDateTime occurredAt
) {
    public CarrierEventCommand {
        eventId = requireText(eventId, "carrier event id");
        carrierCode = requireText(carrierCode, "carrier code");
        trackingNumber = requireText(trackingNumber, "tracking number");
        if (type == null) {
            throw DomainException.validation("carrier event type is required");
        }
        if (occurredAt == null) {
            throw DomainException.validation("carrier event time is required");
        }
        occurredAt = OffsetDateTime.ofInstant(
                occurredAt.toInstant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation(field + " is required");
        }
        return value;
    }
}
