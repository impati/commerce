package com.impati.commerce.order.application.port.in;

public interface OrderHistoryUseCase {
    OrderPage getOrders(OrderQueryKey query);
    OrderHistoryDetail getOwned(String memberId, String orderId);
}
