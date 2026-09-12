package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.CheckoutRequestFingerprint;
import com.impati.commerce.order.domain.CheckoutProgress.Stage;
import com.impati.commerce.order.domain.IdempotencyKey;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 재실행 안전성의 경계인 점유 세대와 멱등 키 유일성을 실제 DB에서 검증한다. */
@SpringBootTest
@RequiresDatabase
class JdbcCheckoutProgressRepositoryTest {
    @Autowired private CheckoutProgressRepository repository;
    @Autowired private OrderChanges orderChanges;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anExpiredLeaseCanBeReclaimedAndRejectsTheOldGeneration() {
        var progress = savedProgress("mem_fence", "key_fence");

        var first = repository.claim(progress.orderId(), Duration.ofMinutes(1)).orElseThrow();
        first.advance(Stage.CART_DETACHED);
        assertThat(repository.save(first, first.leaseGeneration())).isTrue();

        jdbc.update("update checkout_progress set lease_until = date_sub(now(6), interval 1 second) where order_id = ?",
                progress.orderId());
        var second = repository.claim(progress.orderId(), Duration.ofMinutes(1)).orElseThrow();
        first.advance(Stage.INVENTORY_RESERVED);

        assertThat(second.leaseGeneration()).isEqualTo(first.leaseGeneration() + 1);
        assertThat(repository.save(first, first.leaseGeneration())).isFalse();
        assertThat(repository.findByOrderId(progress.orderId()).orElseThrow().stage())
                .isEqualTo(Stage.CART_DETACHED);
    }

    @Test
    void oneMemberCannotAcceptTwoOrdersWithTheSameIdempotencyKey() {
        var first = savedProgress("mem_key", "same_key");
        var secondOrder = saveOrder("mem_key");
        var second = new CheckoutProgress(
                secondOrder.id(), "mem_key", new IdempotencyKey("same_key"),
                new CheckoutRequestFingerprint("b".repeat(64)), "card", 1);

        assertThat(repository.insertIfAbsent(second)).isFalse();
        assertThat(repository.findByMemberAndKey("mem_key", new IdempotencyKey("same_key"))
                .orElseThrow().orderId())
                .isEqualTo(first.orderId());
    }

    @Test
    void manualRequeueRestoresTheStageThatNeededAttention() {
        var progress = savedProgress("mem_attention", "key_attention");
        var claimed = repository.claim(progress.orderId(), Duration.ofMinutes(1)).orElseThrow();
        claimed.advance(Stage.PAYMENT_AUTHORIZED);
        claimed.attention("unclassified failure");
        assertThat(repository.save(claimed, claimed.leaseGeneration())).isTrue();

        assertThat(repository.requeue(progress.orderId(), java.time.OffsetDateTime.now())).isTrue();

        var requeued = repository.findByOrderId(progress.orderId()).orElseThrow();
        assertThat(requeued.stage()).isEqualTo(Stage.PAYMENT_AUTHORIZED);
        assertThat(requeued.resumeStage()).isNull();
    }

    @Test
    void keepsThePaymentTokenUntilAuthorizationAndRemovesItWithTheRecordedPayment() {
        var progress = savedProgress("mem_token", "key_token");
        var claimed = repository.claim(progress.orderId(), Duration.ofMinutes(1)).orElseThrow();

        assertThat(repository.findByOrderId(progress.orderId()).orElseThrow().paymentToken()).isEqualTo("card");

        claimed.payment("pay_token");
        claimed.advance(Stage.PAYMENT_AUTHORIZED);
        assertThat(repository.save(claimed, claimed.leaseGeneration())).isTrue();

        var saved = repository.findByOrderId(progress.orderId()).orElseThrow();
        assertThat(saved.paymentId()).isEqualTo("pay_token");
        assertThat(saved.paymentToken()).isNull();
    }

    private CheckoutProgress savedProgress(String memberId, String key) {
        var order = saveOrder(memberId);
        var progress = new CheckoutProgress(
                order.id(), memberId, new IdempotencyKey(key),
                new CheckoutRequestFingerprint("a".repeat(64)), "card", 1);
        assertThat(repository.insertIfAbsent(progress)).isTrue();
        return progress;
    }

    private Order saveOrder(String memberId) {
        var order = Order.create(
                "ord_" + UUID.randomUUID().toString().replace("-", ""),
                memberId,
                List.of(new OrderLine("sku", "product", "Product", "SKU", 1, Money.krw(1_000))),
                new Address("addr", "home", "Customer", "010", "1 Main", "Seoul", "04524", true));
        orderChanges.commit(order);
        return order;
    }
}
