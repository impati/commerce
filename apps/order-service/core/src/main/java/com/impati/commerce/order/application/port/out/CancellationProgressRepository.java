package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.CancellationProgress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CancellationProgressRepository {
    Optional<CancellationProgress> findByOrderId(String orderId);

    List<CancellationProgress> findByOrderIds(String memberId, List<String> orderIds);

    boolean insertIfAbsent(CancellationProgress progress);

    boolean save(CancellationProgress progress, long leaseGeneration);

    Optional<CancellationProgress> claim(String orderId, Duration leaseDuration);

    List<String> findRecoverable(int batchSize);

    boolean requeue(String orderId, OffsetDateTime now);
}
