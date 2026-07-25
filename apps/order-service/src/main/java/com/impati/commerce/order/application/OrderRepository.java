package com.impati.commerce.order.application;

import com.impati.commerce.order.domain.OrderModels.Order;

import java.util.Collection;
import java.util.Optional;

/**
 * 주문 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface OrderRepository {
    void save(Order order);

    Optional<Order> findById(String orderId);

    Collection<Order> findAll();
}
