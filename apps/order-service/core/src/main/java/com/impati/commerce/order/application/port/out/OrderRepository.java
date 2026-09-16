package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.application.model.OrderCursor;
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

    Optional<Order> findByIdAndMemberId(String orderId, String memberId);

    List<Order> findBy(String memberId, OrderCursor cursor, int size);
}
