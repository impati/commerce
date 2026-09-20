package com.impati.commerce.order.application.port.in;

import com.impati.commerce.order.application.model.OrderCancellationResult;

public interface OrderCancellationUseCase {
    OrderCancellationResult cancel(String memberId, String orderId);
}
