package com.impati.commerce.order.application.component;

import com.impati.commerce.common.Ids;
import com.impati.commerce.order.application.port.in.OrderEventPublishUseCase;
import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문 사건 아웃박스를 비운다 (ADR-0012).
 *
 * <p>기록하는 것과 발행하는 것은 협력자가 겹치지 않으므로 별도 클래스다 — 기록은 주문 변경의
 * 일부라 {@code OrderChanges}가 하고, 발행은 사건 저장소와 발행 포트만 쓴다.
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

    /**
     * 한 주문이 가질 수 있는 사건의 수.
     *
     * <p>사건은 상태 전이에서 나오고 전이는 한 방향이므로 종류마다 한 번이다. 점유 단위가
     * 주문이 되면서 한 번에 집는 사건 수가 <b>주문 수 × 이 값</b>이 되고, 임차가 그것을 덮는지
     * 판정할 때 쓴다. 사건 종류가 늘면 이 값이 함께 늘어 판정이 저절로 엄해진다.
     */
    private static final int MAX_EVENTS_PER_ORDER = OrderEventType.values().length;

    private final OrderEventRepository orderEventRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final int orderBatchSize;
    private final Duration retryDelay;
    private final int maxAttempts;

    /**
     * 설정값이 잘못되면 기동을 실패시킨다.
     *
     * <p>{@code orderBatchSize}가 0이면 후보가 항상 비어 아무것도 집지 않는다.
     * {@code retryDelay}가 0이면 점유가 즉시 만료돼 백오프가 사라지고, 인스턴스가 여럿일 때
     * 배타성도 함께 사라진다. {@code maxAttempts}가 0이면 첫 시도에서 곧바로 포기한다. 셋 다
     * <b>조용히 잘못 도는</b> 실패이며, 그 결과는 사건이 안 나가거나 두 번 나가는 것이다.
     *
     * <p>넷째로 <b>임차가 배치 전체를 덮는지</b> 본다. 한 번에 집은 건을 다 처리하기 전에
     * 임차가 만료되면 아직 처리 중인 건을 다른 인스턴스가 집는다. 그러면 같은 주문을 둘이
     * 갖게 되어 순서 보장이 무너지므로 설정 두 값의 조합으로 깨뜨릴 수 없게 막는다.
     */
    public OrderEventPublishExecutor(
            OrderEventRepository orderEventRepository,
            OrderEventPublisher orderEventPublisher,
            @Value("${orders.event-publish-orders-per-cycle:10}") int orderBatchSize,
            @Value("${orders.event-publish-retry-delay:60s}") Duration retryDelay,
            @Value("${orders.event-publish-max-attempts:5}") int maxAttempts
    ) {
        if (orderBatchSize <= 0) {
            throw new IllegalArgumentException(
                    "orders.event-publish-orders-per-cycle must be positive but was " + orderBatchSize);
        }
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "orders.event-publish-retry-delay must be positive but was " + retryDelay);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "orders.event-publish-max-attempts must be positive but was " + maxAttempts);
        }
        var leaseNeeded = WORST_CASE_PER_EVENT.multipliedBy((long) orderBatchSize * MAX_EVENTS_PER_ORDER);
        if (retryDelay.compareTo(leaseNeeded) < 0) {
            throw new IllegalArgumentException(
                    "orders.event-publish-retry-delay must cover the whole batch: orders-per-cycle "
                            + orderBatchSize + " needs at least " + leaseNeeded + " but was " + retryDelay);
        }
        this.orderEventRepository = orderEventRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.orderBatchSize = orderBatchSize;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 주문을 점유하고 주문별로 순서대로 처리한다 (ADR-0016).
     *
     * <p><b>한 주문의 실패가 그 주문의 뒤 사건을 막는다.</b> 앞 건을 건너뛰고 뒤 건을 보내면
     * 소비자가 보는 순서가 일어난 순서와 달라지므로, 그 주문은 이번 주기를 거기서 멈춘다.
     * 남은 사건은 점유가 밀어둔 시각 뒤에 다시 집힌다.
     *
     * <p><b>다른 주문은 막지 않는다.</b> 순서를 지켜야 하는 범위가 주문 안이므로 주문 사이에는
     * 서로 영향이 없다.
     *
     * <p>실패는 attempts와 last_error로 남고, 한도를 넘으면 FAILED가 되어 조회로 드러난다.
     * 그 시점부터 그 주문의 사건은 더 나가지 않는다 — 순서 있는 스트림에서 한 건을 조용히
     * 건너뛰는 것은 소리 없는 손상이고, 막혀서 드러나는 편이 낫다.
     */
    @Override
    public int publishPending() {
        var publishId = Ids.newId("pub");
        var claimed = orderEventRepository.claimForPublish(publishId, orderBatchSize, retryDelay);
        var settled = 0;
        for (var events : byOrder(claimed).values()) {
            settled += publishInOrder(events);
        }
        if (!claimed.isEmpty()) {
            log.info("order event publish id={} claimed={} settled={}", publishId, claimed.size(), settled);
        }
        return settled;
    }

    /**
     * 주문별로 묶는다. 저장소가 주문끼리 모아 순서대로 돌려주므로 순서를 유지하는 맵을 쓴다.
     */
    private static Map<String, List<OrderEvent>> byOrder(List<OrderEvent> claimed) {
        var grouped = new LinkedHashMap<String, List<OrderEvent>>();
        for (var event : claimed) {
            grouped.computeIfAbsent(event.orderId(), key -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    /** 한 주문의 사건을 순서대로 보낸다. 처음 막히는 곳에서 멈춘다. */
    private int publishInOrder(List<OrderEvent> events) {
        var settled = 0;
        for (var event : events) {
            try {
                if (!publishOnce(event)) {
                    return settled;
                }
                settled++;
            } catch (RuntimeException failure) {
                // 결과를 적는 것까지 실패한 경우다. 점유가 이미 시각을 밀어두었으므로 이 주문은
                // 최소 간격 뒤에 다시 집힌다 — 여기서 복구할 것이 없고, 다른 주문을 계속한다.
                log.error("order event publish aborted, claim keeps the backoff id={}", event.id(), failure);
                return settled;
            }
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
        orderEventRepository.savePublishResult(event);
        return event.isPublished();
    }
}
