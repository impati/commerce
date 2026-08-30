package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.common.Ids;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 인스턴스가 같은 알림을 집지 않는지 확인한다 (ADR-0011).
 *
 * <p>다른 발송 테스트는 전부 단일 스레드라 이 성질을 잡지 못한다. 점유가 배타적이지 않아도
 * 순서대로 부르면 두 번째가 빈 결과를 받으므로 똑같이 통과한다.
 *
 * <p>실제로 동시에 부른다. 점유 조건이 하위 질의 안에만 있으면 두 호출이 같은 묶음을 받고,
 * 그 상태에서는 수락 확인도 막지 못한다 — 둘 다 아직 발송 전이라 나란히 "안 보냈다"를 읽는다.
 */
@SpringBootTest(properties = {
        "notifications.dispatch-interval=3600000"
})
@RequiresDatabase
class ClaimExclusivityTest {

    private static final int ROUNDS = 5;
    private static final int ROWS_PER_ROUND = 6;
    private static final int BATCH = 3;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void twoConcurrentClaimsNeverOverlap() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (var round = 0; round < ROUNDS; round++) {
                for (var index = 0; index < ROWS_PER_ROUND; index++) {
                    var key = "vmail_exclusive_" + round + "_" + index;
                    notificationRepository.saveIfAbsent(Notification.mail(
                            "EmailVerificationRequested", "mem_exclusive",
                            key + "@impati.dev", "subject", "body", key));
                }

                var start = new CountDownLatch(1);
                Callable<List<String>> claim = () -> {
                    start.await();
                    return notificationRepository
                            .claimForDispatch(Ids.newId("dsp"), BATCH, Duration.ofSeconds(600))
                            .stream()
                            .map(Notification::id)
                            .toList();
                };
                var first = pool.submit(claim);
                var second = pool.submit(claim);
                start.countDown();

                var a = first.get(10, TimeUnit.SECONDS);
                var b = second.get(10, TimeUnit.SECONDS);

                var overlap = new java.util.ArrayList<>(a);
                overlap.retainAll(b);
                assertThat(overlap)
                        .as("라운드 %d: 두 점유가 같은 알림을 집으면 그 알림은 두 번 발송된다 (A=%s, B=%s)",
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
}
