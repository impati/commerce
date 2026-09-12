package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderWriter;
import com.impati.commerce.order.application.port.out.TransactionSection;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.OrderModels.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** 주문, 주문 사건과 체크아웃 진행 전이를 한 DB 트랜잭션으로 확정한다. */
@Component
public class CheckoutChanges {
    private final OrderWriter orderWriter;
    private final OrderEventRepository orderEventRepository;
    private final CheckoutProgressRepository checkoutProgressRepository;
    private final TransactionSection transactionSection;

    public CheckoutChanges(
            OrderWriter orderWriter,
            OrderEventRepository orderEventRepository,
            CheckoutProgressRepository checkoutProgressRepository,
            TransactionSection transactionSection
    ) {
        this.orderWriter = orderWriter;
        this.orderEventRepository = orderEventRepository;
        this.checkoutProgressRepository = checkoutProgressRepository;
        this.transactionSection = transactionSection;
    }

    public boolean create(Order order, CheckoutProgress progress) {
        var created = new AtomicBoolean();
        try {
            transactionSection.run(() -> {
                orderWriter.save(order);
                if (!checkoutProgressRepository.insertIfAbsent(progress)) {
                    throw new DuplicateCheckoutException();
                }
                orderEventRepository.saveAll(order.drainPendingEvents());
                created.set(true);
            });
        } catch (DuplicateCheckoutException ignored) {
            return false;
        }
        return created.get();
    }

    public void commit(Order order, CheckoutProgress progress, long leaseGeneration) {
        try {
            transactionSection.run(() -> {
                orderWriter.save(order);
                if (!checkoutProgressRepository.save(progress, leaseGeneration)) {
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

    public void commit(CheckoutProgress progress, long leaseGeneration) {
        try {
            if (!checkoutProgressRepository.save(progress, leaseGeneration)) {
                throw new LeaseLostException(progress.orderId());
            }
        } catch (LeaseLostException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PersistenceFailure(progress.orderId(), failure);
        }
    }

    public static final class LeaseLostException extends RuntimeException {
        public LeaseLostException(String orderId) {
            super("checkout lease lost for " + orderId);
        }
    }

    /** DB에 확정되지 않은 메모리 상태를 다음 단계처럼 다루지 않도록 실행을 즉시 멈춘다. */
    public static final class PersistenceFailure extends RuntimeException {
        public PersistenceFailure(String orderId, RuntimeException cause) {
            super("checkout persistence failed for " + orderId, cause);
        }
    }

    private static final class DuplicateCheckoutException extends RuntimeException {
    }
}
