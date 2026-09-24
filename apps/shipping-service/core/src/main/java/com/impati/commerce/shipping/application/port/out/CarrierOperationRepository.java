package com.impati.commerce.shipping.application.port.out;

import com.impati.commerce.shipping.domain.CarrierOperation;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 택배사 작업의 배타적 점유와 결과 저장을 담당한다. */
public interface CarrierOperationRepository {
    boolean insertIfAbsent(CarrierOperation operation);

    Optional<CarrierOperation> find(String idempotencyKey);

    Optional<CarrierOperation> claim(String idempotencyKey, Duration leaseDuration);

    List<CarrierOperation> claimDue(int batchSize, Duration leaseDuration);

    boolean succeed(String idempotencyKey, long claimGeneration, OffsetDateTime completedAt);

    boolean retry(String idempotencyKey, long claimGeneration, String error, OffsetDateTime nextAttemptAt);

    boolean reject(String idempotencyKey, long claimGeneration, String error, OffsetDateTime completedAt);

    boolean requireAttention(String idempotencyKey, long claimGeneration, String error, OffsetDateTime completedAt);
}
