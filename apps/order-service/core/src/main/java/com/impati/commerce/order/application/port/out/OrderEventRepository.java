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
     * 지금 발행할 수 있는 <b>주문</b>을 점유하고 그 주문들의 미발행 사건을 돌려준다 (ADR-0016).
     *
     * <p>이름이 {@code find}가 아닌 이유는 <b>쓰는 조회</b>이기 때문이다. 다음 시도 시각을
     * {@code retryDelay} 뒤로 밀어 다른 인스턴스가 같은 주문을 집지 못하게 하고, 그 밀어둔
     * 시각이 실패했을 때의 재시도 간격이 된다 — 실패 경로에 쓰기가 없어야 하므로 성공을
     * 전제하지 않는다.
     *
     * <p><b>점유 단위가 주문이다.</b> 사건 단위로 집으면 같은 주문의 두 사건을 다른 인스턴스가
     * 나눠 가질 수 있고, 그러면 둘 다 성공해도 끝나는 순서가 발행 순서가 된다. 한 주문을 한
     * 인스턴스만 갖게 하는 것이 순서 보장의 근거다.
     *
     * <p>돌려주는 묶음은 <b>주문별로 모여 있고 주문 안에서는 일어난 순서</b>다. 부르는 쪽은 그
     * 순서대로 보내야 하며, 한 건이 실패하면 같은 주문의 뒤 건을 보내서는 안 된다.
     *
     * <p>어떤 주문의 사건 일부만 집혔으면 그 주문은 <b>통째로 빠진다.</b> 앞 건을 건너뛴 채 뒤
     * 건을 보내는 것을 막기 위해서다. 남겨둔 것은 다음 주기가 온전히 집는다.
     *
     * <p>{@code publishId}는 이 주기가 집은 묶음을 가리킨다. 같은 값으로 두 번 부르지 않는다.
     *
     * <p>{@code orderBatchSize}는 <b>주문의 수</b>이지 사건의 수가 아니다. 밀린 건수가 한 주기의
     * 길이를 정하지 않게 하려는 것이며, 그 건수가 커지는 시점이 정확히 소비자 장애 중이다.
     * 임차는 이 묶음 전체를 처리하는 동안 유지돼야 하므로 {@code retryDelay}는 그보다 넉넉해야
     * 한다.
     */
    List<OrderEvent> claimForPublish(String publishId, int orderBatchSize, Duration retryDelay);

    /** 발행 결과를 기록한다. 사건의 사실 부분은 바뀌지 않으므로 발행 상태만 쓴다. */
    void savePublishResult(OrderEvent event);
}
