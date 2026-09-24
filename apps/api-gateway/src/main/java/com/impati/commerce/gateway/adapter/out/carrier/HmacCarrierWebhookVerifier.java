package com.impati.commerce.gateway.adapter.out.carrier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.gateway.application.port.out.CarrierWebhookVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;

/** HMAC으로 요청을 검증하고 현재 고정 택배사의 상태를 공통 사건으로 바꾼다. */
@Component
public class HmacCarrierWebhookVerifier implements CarrierWebhookVerifier {
    private static final Map<String, String> STATUS_TYPES = Map.of(
            "PICKED_UP", "PICKED_UP",
            "IN_TRANSIT", "IN_TRANSIT",
            "DELIVERED", "DELIVERED",
            "DELIVERY_FAILED", "DELIVERY_FAILED",
            "RETURNED", "RETURNED"
    );

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;
    private final Duration tolerance;
    private final String carrierCode;

    public HmacCarrierWebhookVerifier(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${gateway.carrier-webhook.secret}") String secret,
            @Value("${gateway.carrier-webhook.tolerance:PT5M}") Duration tolerance,
            @Value("${gateway.carrier-webhook.carrier-code:PRIMARY}") String carrierCode
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tolerance = tolerance;
        this.carrierCode = carrierCode;
    }

    @Override
    public VerifiedCarrierEvent verify(String eventId, String timestamp, String signature, String body) {
        requireHeader(eventId, "event id");
        requireHeader(timestamp, "timestamp");
        requireHeader(signature, "signature");
        var sentAt = timestamp(timestamp);
        if (Duration.between(sentAt, clock.instant()).abs().compareTo(tolerance) > 0) {
            throw unauthorized("carrier webhook timestamp is outside the allowed window");
        }
        var expected = "sha256=" + hmac(eventId + "\n" + timestamp + "\n" + body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw unauthorized("carrier webhook signature is invalid");
        }

        try {
            var payload = objectMapper.readValue(body, CarrierPayload.class);
            var type = STATUS_TYPES.get(payload.status());
            if (type == null || payload.trackingNumber() == null || payload.trackingNumber().isBlank()
                    || payload.occurredAt() == null) {
                throw DomainException.validation("carrier webhook payload is invalid");
            }
            return new VerifiedCarrierEvent(eventId, carrierCode, payload.trackingNumber(), type,
                    OffsetDateTime.parse(payload.occurredAt()));
        } catch (DomainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw DomainException.validation("carrier webhook payload is invalid");
        }
    }

    private Instant timestamp(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException failure) {
            throw unauthorized("carrier webhook timestamp is invalid");
        }
    }

    private String hmac(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("carrier webhook verifier could not initialize", failure);
        }
    }

    private static void requireHeader(String value, String name) {
        if (value == null || value.isBlank()) throw unauthorized("carrier webhook " + name + " is required");
    }

    private static DomainException unauthorized(String message) {
        return new DomainException("invalid_carrier_webhook", message, 401);
    }

    private record CarrierPayload(String trackingNumber, String status, String occurredAt) { }
}
