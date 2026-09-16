package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.IdempotencyKey;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutProgressRepository {
    Optional<CheckoutProgress> findByOrderId(String orderId);

    List<CheckoutProgress> findByOrderIds(String memberId, List<String> orderIds);

    Optional<CheckoutProgress> findByMemberAndKey(String memberId, IdempotencyKey idempotencyKey);

    boolean insertIfAbsent(CheckoutProgress progress);

    boolean save(CheckoutProgress progress, long leaseGeneration);

    Optional<CheckoutProgress> claim(String orderId, Duration leaseDuration);

    List<String> findRecoverable(int batchSize);

    boolean requeue(String orderId, OffsetDateTime now);
}
