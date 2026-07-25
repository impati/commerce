package com.impati.commerce.order.application;

import com.impati.commerce.order.domain.OrderModels.Order;

import java.util.Optional;

/**
 * 주문 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>저장은 aggregate 전체를 덮어쓴다. 조회로 얻은 {@link Order}를 변형한 것만으로
 * 저장됐다고 가정하지 말고 {@link #save}를 명시적으로 부른다.
 */
public interface OrderRepository {
    void save(Order order);

    Optional<Order> findById(String orderId);
}
