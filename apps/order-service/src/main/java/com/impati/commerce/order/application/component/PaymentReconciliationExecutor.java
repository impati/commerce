package com.impati.commerce.order.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.in.PaymentReconciliationSummary;
import com.impati.commerce.order.application.port.in.PaymentReconciliationUseCase;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.domain.OrderModels.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 결제 미확인으로 표시된 주문을 정리한다 (PD-0015).
 *
 * <p>절차는 <b>먼저 묻고 그 다음 조치한다</b>다. 표시는 "매입됐는지 모른다"는 뜻이므로 무엇을
 * 할지는 결제의 현재 상태가 정한다 (PD-0015-R2). 묻지 않고 환불하면 매입되지 않은 결제를
 * 환불하려 하고, 묻지 않고 취소하면 매입된 결제를 취소하려 한다.
 *
 * <p>정리는 주문 상태를 바꾸지 않는다 (PD-0015-R5). 주문은 이미 취소로 끝났고 여기서 확정하는
 * 것은 결제 쪽 사실뿐이다.
 */
@Component
public class PaymentReconciliationExecutor implements PaymentReconciliationUseCase {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationExecutor.class);

    private static final String AUTHORIZED = "AUTHORIZED";
    private static final String CAPTURED = "CAPTURED";
    private static final String CANCELLED = "CANCELLED";
    private static final String REFUNDED = "REFUNDED";

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final int batchSize;
    private final Duration retryDelay;

    public PaymentReconciliationExecutor(
            OrderRepository orderRepository,
            PaymentClient paymentClient,
            @Value("${orders.payment-reconcile-batch-size:50}") int batchSize,
            @Value("${orders.payment-reconcile-retry-delay:60s}") Duration retryDelay
    ) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
    }

    /**
     * 후보를 훑고 건별로 점유해 정리한다.
     *
     * <p>점유를 후보 전체에 미리 걸지 않고 처리 직전에 하나씩 거는 이유는, 점유가 덮어야 하는
     * 시간이 그 한 건의 작업 시간이면 되기 때문이다 (ADR-0009). 미리 걸면 배치가 길어질 때
     * 앞쪽 건의 점유가 처리 중에 만료돼 다른 인스턴스가 같은 일을 중복한다.
     *
     * <p>한 건의 실패가 나머지를 막지 않는다. 실패한 건은 표시가 남아 다음 주기의 대상이 되고
     * (PD-0015-R6), 점유가 시각을 이미 밀어두었으므로 최소 간격 안에는 다시 집히지 않는다
     * (PD-0015-R8) — 실패 경로에 쓰기가 없어야 하므로 여기서 아무것도 쓰지 않는다.
     */
    @Override
    public PaymentReconciliationSummary reconcileUnknownPaymentOutcomes() {
        var candidates = orderRepository.findPaymentReconciliationCandidates(batchSize);
        var claimed = 0;
        var resolved = 0;
        for (var orderId : candidates) {
            var order = orderRepository.claimForPaymentReconciliation(orderId, retryDelay);
            if (order.isEmpty()) {
                continue;
            }
            claimed++;
            try {
                reconcile(order.get());
                resolved++;
            } catch (RuntimeException failure) {
                log.error("payment reconciliation failed, mark stays order={}", orderId, failure);
            }
        }
        if (claimed > 0) {
            log.info("payment reconciliation candidates={} claimed={} resolved={}",
                    candidates.size(), claimed, resolved);
        }
        return new PaymentReconciliationSummary(candidates.size(), claimed, resolved);
    }

    /**
     * 상태에 맞는 조치를 하고 표시를 해제한다 (PD-0015-R2, PD-0015-R3, PD-0015-R4).
     *
     * <p>승인 상태로 남아 있는 것은 체크아웃 롤백의 취소 호출까지 실패했다는 뜻이다. 그 승인은
     * 아무도 매입하지 않을 것이므로 종단 상태로 보낸다. 환불이 아니라 취소인 것은 환불이 매입된
     * 결제만 대상이고 (PD-0011-R8), 승인 취소는 고객 명세서에 흔적을 남기지 않기 때문이다.
     *
     * <p>모르는 상태는 표시를 남긴다. 조치를 추측하면 대금을 잘못 움직인다.
     */
    private void reconcile(Order order) {
        var payment = paymentClient.payment(order.paymentId());
        switch (payment.status()) {
            case CAPTURED -> paymentClient.refundPayment(payment.id());
            case AUTHORIZED -> paymentClient.cancelPayment(payment.id());
            case CANCELLED, REFUNDED -> {
                // 이미 종단 상태다. 되돌릴 대금이 없으므로 표시만 해제한다.
            }
            default -> throw DomainException.conflict(
                    "unexpected payment status for reconciliation: " + payment.status());
        }
        order.resolvePaymentOutcome();
        orderRepository.save(order);
    }
}
