package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderWriter;
import com.impati.commerce.order.application.port.out.TransactionSection;
import com.impati.commerce.order.domain.CancellationProgress;
import com.impati.commerce.order.domain.OrderModels.Order;
import org.springframework.stereotype.Component;

/** 주문 사건과 고객 취소 진행 전이를 한 DB 트랜잭션으로 확정한다. */
@Component
public class CancellationChanges {
    private final OrderWriter orderWriter;
    private final OrderEventRepository orderEventRepository;
    private final CancellationProgressRepository progressRepository;
    private final TransactionSection transactionSection;

    public CancellationChanges(
            OrderWriter orderWriter,
            OrderEventRepository orderEventRepository,
            CancellationProgressRepository progressRepository,
            TransactionSection transactionSection
    ) {
        this.orderWriter = orderWriter;
        this.orderEventRepository = orderEventRepository;
        this.progressRepository = progressRepository;
        this.transactionSection = transactionSection;
    }

    public boolean create(CancellationProgress progress) {
        return progressRepository.insertIfAbsent(progress);
    }

    public void commit(CancellationProgress progress, long leaseGeneration) {
        try {
            if (!progressRepository.save(progress, leaseGeneration)) {
                throw new LeaseLostException(progress.orderId());
            }
        } catch (LeaseLostException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PersistenceFailure(progress.orderId(), failure);
        }
    }

    public void commit(Order order, CancellationProgress progress, long leaseGeneration) {
        try {
            transactionSection.run(() -> {
                orderWriter.save(order);
                if (!progressRepository.save(progress, leaseGeneration)) {
                    throw new LeaseLostException(progress.orderId());
                }
                orderEventRepository.saveAll(order.drainPendingEvents());
            });
        } catch (LeaseLostException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PersistenceFailure(progress.orderId(), failure);
        }
    }

    public static final class LeaseLostException extends RuntimeException {
        public LeaseLostException(String orderId) {
            super("cancellation lease lost for " + orderId);
        }
    }

    public static final class PersistenceFailure extends RuntimeException {
        public PersistenceFailure(String orderId, RuntimeException cause) {
            super("cancellation persistence failed for " + orderId, cause);
        }
    }
}
