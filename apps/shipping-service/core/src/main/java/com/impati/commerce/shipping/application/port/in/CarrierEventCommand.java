package com.impati.commerce.shipping.application.port.in;

import java.time.OffsetDateTime;

public record CarrierEventCommand(
        String eventId,
        String carrierCode,
        String trackingNumber,
        String type,
        OffsetDateTime occurredAt
) { }
