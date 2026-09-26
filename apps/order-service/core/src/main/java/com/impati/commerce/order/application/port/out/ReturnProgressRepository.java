package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.ReturnProgress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReturnProgressRepository {
    boolean insertIfAbsent(ReturnProgress progress);
    void save(ReturnProgress progress);
    Optional<ReturnProgress> findById(String returnId);
    Optional<ReturnProgress> findByIdForUpdate(String returnId);
    Optional<ReturnProgress> findByOrderId(String orderId);
    List<ReturnProgress> findRecoverable(OffsetDateTime now, int limit);
}
