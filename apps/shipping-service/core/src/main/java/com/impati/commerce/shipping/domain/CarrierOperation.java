package com.impati.commerce.shipping.domain;

import java.time.OffsetDateTime;

/** 택배사 명령을 프로세스 종료와 응답 유실 뒤에도 같은 멱등키로 복구하기 위한 작업. */
public record CarrierOperation(
        String idempotencyKey,
        String shipmentId,
        Type type,
        Status status,
        long claimGeneration,
        OffsetDateTime claimUntil,
        OffsetDateTime nextAttemptAt,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public enum Type {
        REGISTER,
        CANCEL
    }

    public enum Status {
        PENDING,
        SUCCEEDED,
        REJECTED,
        ATTENTION_REQUIRED
    }

    public static String registrationKey(String shipmentId) {
        return "carrier-registration:" + shipmentId;
    }

    public static String cancellationKey(String shipmentId) {
        return "carrier-cancellation:" + shipmentId;
    }

    public static CarrierOperation pending(String key, String shipmentId, Type type, OffsetDateTime now) {
        return new CarrierOperation(key, shipmentId, type, Status.PENDING, 0, null, now, 0, null, now, now);
    }
}
