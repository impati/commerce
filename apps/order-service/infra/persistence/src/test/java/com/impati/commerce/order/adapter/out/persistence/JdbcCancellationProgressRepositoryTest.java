package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.domain.CancellationProgress;
import com.impati.commerce.order.domain.CancellationProgress.Stage;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.test.RequiresDatabase;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresDatabase
class JdbcCancellationProgressRepositoryTest {

    @Autowired
    private CancellationProgressRepository repository;
    @Autowired
    private OrderChanges orderChanges;
    @Autowired
    private JdbcTemplate jdbc;

    /** [PD-0024-R8] 저장소가 주문별 취소 진행을 하나로 제한한다. */
    @Test
    void oneOrderHasOneCancellationProgress() {
        var order = saveOrder("mem_cancel_unique");

        assertThat(repository.insertIfAbsent(new CancellationProgress(order.id(), order.memberId()))).isTrue();
        assertThat(repository.insertIfAbsent(new CancellationProgress(order.id(), order.memberId()))).isFalse();
    }

    /** [PD-0024-R7] 만료된 임차를 회수하면 이전 실행자의 저장을 fencing token이 막는다. */
    @Test
    void reclaimedLeaseRejectsTheOldGeneration() {
        var progress = savedProgress("mem_cancel_fence");
        var first = repository.claim(progress.orderId(), Duration.ofMinutes(1)).orElseThrow();
        first.advance(Stage.SHIPMENT_CANCELLED);
        assertThat(repository.save(first, first.leaseGeneration())).isTrue();

        jdbc.update("update cancellation_progress set lease_until = date_sub(now(6), interval 1 second) where order_id = ?",
                progress.orderId());
        var second = repository.claim(progress.orderId(), Duration.ofMinutes(1)).orElseThrow();
        first.advance(Stage.PAYMENT_REFUNDED);

        assertThat(second.leaseGeneration()).isEqualTo(first.leaseGeneration() + 1);
        assertThat(repository.save(first, first.leaseGeneration())).isFalse();
        assertThat(repository.findByOrderId(progress.orderId()).orElseThrow().stage())
                .isEqualTo(Stage.SHIPMENT_CANCELLED);
    }

    /** [PD-0024-R10] 운영 확인 뒤에는 문제가 난 단계로 되돌아가 재개한다. */
    @Test
    void manualRequeueRestoresAttentionStage() {
        var progress = savedProgress("mem_cancel_attention");
        var claimed = repository.claim(progress.orderId(), Duration.ofMinutes(1)).orElseThrow();
        claimed.advance(Stage.PAYMENT_REFUNDED);
        claimed.attention("inventory mismatch");
        assertThat(repository.save(claimed, claimed.leaseGeneration())).isTrue();

        assertThat(repository.requeue(progress.orderId(), OffsetDateTime.now())).isTrue();

        var requeued = repository.findByOrderId(progress.orderId()).orElseThrow();
        assertThat(requeued.stage()).isEqualTo(Stage.PAYMENT_REFUNDED);
        assertThat(requeued.resumeStage()).isNull();
    }

    private CancellationProgress savedProgress(String memberId) {
        var order = saveOrder(memberId);
        var progress = new CancellationProgress(order.id(), memberId);
        assertThat(repository.insertIfAbsent(progress)).isTrue();
        return progress;
    }

    private Order saveOrder(String memberId) {
        var order = Order.create(
                "ord_" + UUID.randomUUID().toString().replace("-", ""),
                memberId,
                List.of(new OrderLine("sku", "product", "Product", "SKU", 1, Money.krw(1_000))),
                new Address("addr", "home", "Customer", "010", "1 Main", "Seoul", "04524", true),
                LocalDateTime.now());
        orderChanges.commit(order);
        return order;
    }
}
