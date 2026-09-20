package com.impati.commerce.order.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.model.OrderCancellationResult;
import com.impati.commerce.order.application.port.in.OrderCancellationUseCase;
import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CancellationProgress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean({InventoryClient.class, PaymentClient.class, ShippingClient.class})
public class OrderCancellationExecutor implements OrderCancellationUseCase {
    private final OrderRepository orderRepository;
    private final CheckoutProgressRepository checkoutProgressRepository;
    private final CancellationProgressRepository progressRepository;
    private final CancellationChanges changes;
    private final CancellationExecution execution;

    public OrderCancellationExecutor(
            OrderRepository orderRepository,
            CheckoutProgressRepository checkoutProgressRepository,
            CancellationProgressRepository progressRepository,
            CancellationChanges changes,
            CancellationExecution execution
    ) {
        this.orderRepository = orderRepository;
        this.checkoutProgressRepository = checkoutProgressRepository;
        this.progressRepository = progressRepository;
        this.changes = changes;
        this.execution = execution;
    }

    @Override
    public OrderCancellationResult cancel(String memberId, String orderId) {
        var order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> DomainException.notFound("order not found"));
        var checkout = checkoutProgressRepository.findByOrderId(orderId)
                .orElseThrow(() -> DomainException.cancellationNotAllowed("only a successful checkout can be cancelled"));
        if (!checkout.isCompletedSuccessfully()) {
            throw DomainException.cancellationNotAllowed("only a successful checkout can be cancelled");
        }
        var progress = progressRepository.findByOrderId(orderId).orElse(null);
        if (progress == null) {
            order.ensureCustomerCancellationMayStart();
            var created = new CancellationProgress(orderId, memberId);
            if (changes.create(created)) {
                progress = created;
            } else {
                progress = progressRepository.findByOrderId(orderId)
                        .orElseThrow(() -> DomainException.conflict("cancellation creation raced without a result"));
            }
        }
        if (!progress.memberId().equals(memberId)) {
            throw DomainException.notFound("order not found");
        }
        if (progress.isCompleted()) {
            return new OrderCancellationResult(orderId, progress.customerStatus().name());
        }
        if (progress.isRejected()) {
            throw DomainException.cancellationNotAllowed("shipment already left and cannot be cancelled");
        }

        progressRepository.claim(orderId, CancellationRecoveryExecutor.LEASE_DURATION).ifPresent(execution::run);
        var current = progressRepository.findByOrderId(orderId).orElseThrow();
        if (current.isRejected()) {
            throw DomainException.cancellationNotAllowed("shipment already left and cannot be cancelled");
        }
        return new OrderCancellationResult(orderId, current.customerStatus().name());
    }
}
