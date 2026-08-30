package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.OrderModels.Order;

/**
 * 주문 애그리거트를 쓰는 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p><b>{@code OrderChanges}만 이 포트를 쓴다.</b> 읽기가 있는 {@link OrderRepository}에서 쓰기를
 * 떼어낸 이유가 그것이다 — 주문의 상태 변경과 그로부터 나온 사건은 함께 성립해야 하는데,
 * 저장이 아무 데서나 가능하면 그 규칙이 문장으로만 남는다. 여기를 좁혀두면 사건 없이 주문을
 * 저장하는 코드를 <b>쓸 수 없다</b>.
 *
 * <p>이 포트는 트랜잭션 경계를 만들지 않는다. 무엇이 한 단위인지는 부르는 쪽이 정한다.
 */
public interface OrderWriter {
    void save(Order order);
}
