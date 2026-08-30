package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.OrderModels.OrderEvent;

import java.time.Duration;
import java.util.List;

/**
 * 주문 사건 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>{@link OrderRepository}에서 나눈 이유는 다루는 것이 다르기 때문이다 — 저쪽은 주문의 현재
 * 상태이고 이쪽은 일어난 사실의 기록이다. 수명도 다르다. 사건은 발행되면 더 이상 바뀌지 않는다.
 */
public interface OrderEventRepository {
    /**
     * 사건을 기록한다.
     *
     * <p>주문 저장과 함께 성립해야 하므로 {@code OrderChanges}만 이 메서드를 쓴다. 여기서
     * 트랜잭션을 열지 않는 것도 그래서다 — 무엇이 한 단위인지는 부르는 쪽이 정한다.
     */
    void saveAll(List<OrderEvent> events);

    /**
     * 지금 발행할 수 있는 사건을 한 번에 점유하고 그 묶음을 돌려준다 (ADR-0012).
     *
     * <p>이름이 {@code find}가 아닌 이유는 <b>쓰는 조회</b>이기 때문이다. 다음 시도 시각을
     * {@code retryDelay} 뒤로 밀어 다른 인스턴스가 같은 사건을 집지 못하게 하고, 그 밀어둔
     * 시각이 실패했을 때의 재시도 간격이 된다 — 실패 경로에 쓰기가 없어야 하므로 성공을
     * 전제하지 않는다.
     *
     * <p>{@code publishId}는 이 주기가 집은 묶음을 가리킨다. 같은 값으로 두 번 부르지 않는다.
     *
     * <p>한 번에 집는 수를 제한하는 것은 밀린 건수가 한 주기의 길이를 정하지 않게 하려는
     * 것이다. 그 건수가 커지는 시점이 정확히 소비자 장애 중이다. 임차는 이 묶음 전체를
     * 처리하는 동안 유지돼야 하므로 {@code retryDelay}는 배치 길이보다 넉넉해야 한다.
     */
    List<OrderEvent> claimForPublish(String publishId, int batchSize, Duration retryDelay);

    /** 발행 결과를 기록한다. 사건의 사실 부분은 바뀌지 않으므로 발행 상태만 쓴다. */
    void savePublishResult(OrderEvent event);
}
