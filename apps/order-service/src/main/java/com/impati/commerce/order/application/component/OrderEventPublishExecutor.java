package com.impati.commerce.order.application.component;

import com.impati.commerce.common.Ids;
import com.impati.commerce.order.application.port.in.OrderEventPublishUseCase;
import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 주문 사건 아웃박스를 비운다 (ADR-0012).
 *
 * <p>기록하는 것과 발행하는 것은 협력자가 겹치지 않으므로 별도 클래스다 — 기록하는 쪽은
 * 주문 저장소와 여섯 협력자를 쓰고, 발행하는 쪽은 저장소와 발행 포트만 쓴다.
 */
@Component
public class OrderEventPublishExecutor implements OrderEventPublishUseCase {
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublishExecutor.class);

    /**
     * 한 건을 처리하는 최악 시간.
     *
     * <p>발행 한 번의 외부 호출이고 common-http 기본 타임아웃(연결 1초 · 읽기 3초)에 묶여 있다.
     * 임차가 배치 전체를 덮는지 판정하는 기준이며, 타임아웃 기본값이 바뀌거나 발행이 호출을
     * 하나 더 하게 되면 이 값도 함께 움직여야 한다.
     */
    private static final Duration WORST_CASE_PER_EVENT = Duration.ofSeconds(4);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final int batchSize;
    private final Duration retryDelay;
    private final int maxAttempts;

    /**
     * 설정값이 잘못되면 기동을 실패시킨다.
     *
     * <p>{@code batchSize}가 0이면 후보가 항상 비어 아무것도 집지 않는다. {@code retryDelay}가
     * 0이면 점유가 즉시 만료돼 백오프가 사라지고, 인스턴스가 여럿일 때 배타성도 함께 사라진다.
     * {@code maxAttempts}가 0이면 첫 시도에서 곧바로 포기한다. 셋 다 <b>조용히 잘못 도는</b>
     * 실패이며, 그 결과는 사건이 안 나가거나 두 번 나가는 것이다.
     *
     * <p>넷째로 <b>임차가 배치 전체를 덮는지</b> 본다. 한 번에 집은 건을 다 처리하기 전에
     * 임차가 만료되면 아직 처리 중인 건을 다른 인스턴스가 집는다. 이 관계가 깨지면 묶음 점유의
     * 전제가 무너지므로 설정 두 값의 조합으로 깨뜨릴 수 없게 막는다.
     */
    public OrderEventPublishExecutor(
            OrderRepository orderRepository,
            OrderEventPublisher orderEventPublisher,
            @Value("${orders.event-publish-batch-size:10}") int batchSize,
            @Value("${orders.event-publish-retry-delay:60s}") Duration retryDelay,
            @Value("${orders.event-publish-max-attempts:5}") int maxAttempts
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "orders.event-publish-batch-size must be positive but was " + batchSize);
        }
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "orders.event-publish-retry-delay must be positive but was " + retryDelay);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "orders.event-publish-max-attempts must be positive but was " + maxAttempts);
        }
        var leaseNeeded = WORST_CASE_PER_EVENT.multipliedBy(batchSize);
        if (retryDelay.compareTo(leaseNeeded) < 0) {
            throw new IllegalArgumentException(
                    "orders.event-publish-retry-delay must cover the whole batch: batch-size "
                            + batchSize + " needs at least " + leaseNeeded + " but was " + retryDelay);
        }
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 발행할 수 있는 것들을 한 번에 점유하고 건별로 처리한다.
     *
     * <p>점유가 한 문장이므로 후보 건수와 무관하게 왕복이 하나다 (ADR-0011).
     *
     * <p>한 건의 실패가 다음 건을 막지 않는다. 실패는 attempts와 last_error로 남고, 한도를
     * 넘으면 FAILED가 되어 조회로 드러난다. 예외를 삼켜 사라지게 하지 않는다 — 실패가 상태로
     * 남지 않으면 빈도조차 알 수 없다는 것이 이 작업의 출발점이었다.
     */
    @Override
    public int publishPending() {
        var publishId = Ids.newId("pub");
        var claimed = orderRepository.claimForPublish(publishId, batchSize, retryDelay);
        var settled = 0;
        for (var event : claimed) {
            try {
                if (publishOnce(event)) {
                    settled++;
                }
            } catch (RuntimeException failure) {
                // 결과를 적는 것까지 실패한 경우다. 점유가 이미 시각을 밀어두었으므로 이 건은
                // 최소 간격 뒤에 다시 집힌다 — 여기서 복구할 것이 없고, 나머지 건을 계속한다.
                log.error("order event publish aborted, claim keeps the backoff id={}", event.id(), failure);
            }
        }
        if (!claimed.isEmpty()) {
            log.info("order event publish id={} claimed={} settled={}", publishId, claimed.size(), settled);
        }
        return settled;
    }

    private boolean publishOnce(OrderEvent event) {
        try {
            orderEventPublisher.publish(event);
            event.markPublished();
        } catch (RuntimeException failure) {
            event.markFailed(failure.getMessage(), maxAttempts);
            log.warn("order event publish failed id={} type={} attempts={}",
                    event.id(), event.type(), event.attempts());
        }
        orderRepository.savePublishResult(event);
        return event.isPublished();
    }
}
