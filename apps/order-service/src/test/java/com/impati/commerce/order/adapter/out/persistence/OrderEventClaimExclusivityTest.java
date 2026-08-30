package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.Ids;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 인스턴스가 같은 사건을 집지 않는지 확인한다 (ADR-0012).
 *
 * <p>다른 발행 테스트는 전부 단일 스레드라 이 성질을 잡지 못한다. 점유가 배타적이지 않아도
 * 순서대로 부르면 두 번째가 빈 결과를 받으므로 똑같이 통과한다.
 *
 * <p>실제로 동시에 부른다. 점유 조건이 하위 질의 안에만 있고 {@code skip locked}가 없으면 두
 * 호출이 같은 묶음을 받는다 — 바깥 {@code where}에 남는 것이 {@code id in (...)}뿐이라 남이
 * 먼저 점유하고 커밋해도 그 재검사를 통과하기 때문이다.
 */
@SpringBootTest(properties = {
        "orders.event-publish-interval=3600000",
        "orders.payment-reconcile-interval=3600000"
})
@RequiresDatabase
class OrderEventClaimExclusivityTest {

    private static final int ROUNDS = 5;
    private static final int ORDERS_PER_ROUND = 6;
    private static final int BATCH = 3;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private OrderChanges orderChanges;

    @Test
    void twoConcurrentClaimsNeverOverlap() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (var round = 0; round < ROUNDS; round++) {
                for (var index = 0; index < ORDERS_PER_ROUND; index++) {
                    orderChanges.commit(newOrder("mem_exclusive_" + round + "_" + index));
                }

                var start = new CountDownLatch(1);
                Callable<List<String>> claim = () -> {
                    start.await();
                    return orderEventRepository
                            .claimForPublish(Ids.newId("pub"), BATCH, Duration.ofSeconds(600))
                            .stream()
                            .map(OrderEvent::id)
                            .toList();
                };
                var first = pool.submit(claim);
                var second = pool.submit(claim);
                start.countDown();

                var a = first.get(10, TimeUnit.SECONDS);
                var b = second.get(10, TimeUnit.SECONDS);

                var overlap = new ArrayList<>(a);
                overlap.retainAll(b);
                assertThat(overlap)
                        .as("라운드 %d: 두 점유가 같은 사건을 집으면 그 사건은 두 번 발행된다 (A=%s, B=%s)",
                                round, a.size(), b.size())
                        .isEmpty();
                assertThat(a.size() + b.size())
                        .as("라운드 %d: 적어도 한쪽은 일을 받아야 한다", round)
                        .isPositive();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Order newOrder(String memberId) {
        return new Order(
                memberId,
                List.of(new OrderLine("sku_tee_white_m", "prd_tee", "Tee", "White M", 1, Money.krw(29_000))),
                new Address("adr_x", "home", "Demo Customer", "010", "1 Main", "Seoul", "04524", true)
        );
    }
}
