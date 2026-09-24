package com.impati.commerce.gateway.application.port.out;

import java.time.OffsetDateTime;

/** 택배사별 인증과 원본 상태 해석을 Gateway 경계 안에 가둔다. */
public interface CarrierWebhookVerifier {
    VerifiedCarrierEvent verify(String eventId, String timestamp, String signature, String body);

    record VerifiedCarrierEvent(
            String eventId,
            String carrierCode,
            String trackingNumber,
            String type,
            OffsetDateTime occurredAt
    ) { }
}
