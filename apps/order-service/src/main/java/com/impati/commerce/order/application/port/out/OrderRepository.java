package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.OrderModels.Order;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 주문 조회 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p><b>저장이 여기 없다.</b> 주문의 상태 변경과 그로부터 나온 사건은 함께 성립해야 하는
 * 응집 단위이고, 그 단위를 {@code OrderChanges}가 소유한다 (ADR-0012). 쓰기를 여기 두면
 * 사건 없이 저장하는 경로가 열리고 규칙이 문장으로만 남는다.
 *
 * <p>조회로 얻은 {@link Order}를 변형한 것만으로 저장됐다고 가정하지 말 것. 변경했으면
 * {@code OrderChanges}에 명시적으로 넘긴다.
 */
public interface OrderRepository {
    Optional<Order> findById(String orderId);

    /**
     * 지금 정리할 수 있는 결제 미확인 주문의 식별자 (PD-0012-R12, PD-0015-R1).
     *
     * <p>점유하지 않는다. 여기서 얻은 식별자를 {@link #claimForPaymentReconciliation}으로
     * 하나씩 집어야 실제로 처리할 수 있다.
     *
     * <p>한 번에 가져오는 수를 제한하는 것은 밀린 건수가 한 주기의 길이를 정하지 않게 하려는
     * 것이다. 그 건수가 커지는 시점이 정확히 결제 서비스 장애 중이다 (ADR-0009).
     */
    List<String> findPaymentReconciliationCandidates(int batchSize);

    /**
     * 주문 한 건을 정리하려고 점유한다 (PD-0015-R7, PD-0015-R8).
     *
     * <p>이름이 {@code find}가 아닌 이유는 <b>쓰는 조회</b>이기 때문이다. 다음 시도 시각을
     * {@code retryDelay} 뒤로 밀어 다른 인스턴스가 같은 주문을 집지 못하게 하고, 그 밀어둔
     * 시각이 실패했을 때의 재시도 간격이 된다 — 실패 경로에 쓰기가 없어야 하므로 성공을
     * 전제하지 않는다.
     *
     * <p>점유에 실패하면 비어 있다. 다른 인스턴스가 이미 집었거나, 표시가 이미 해제됐거나,
     * 아직 시도 시각이 아닌 경우다. 세 경우 모두 지금 할 일이 없다는 뜻이므로 구분하지 않는다.
     */
    Optional<Order> claimForPaymentReconciliation(String orderId, Duration retryDelay);

}
