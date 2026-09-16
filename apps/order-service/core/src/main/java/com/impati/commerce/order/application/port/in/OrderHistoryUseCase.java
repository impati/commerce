package com.impati.commerce.order.application.port.in;

import com.impati.commerce.order.application.model.OrderHistoryDetail;
import com.impati.commerce.order.application.model.OrderPage;
import com.impati.commerce.order.application.model.OrderQueryKey;

public interface OrderHistoryUseCase {
    OrderPage getOrders(OrderQueryKey query);
    OrderHistoryDetail getOwned(String memberId, String orderId);
}
