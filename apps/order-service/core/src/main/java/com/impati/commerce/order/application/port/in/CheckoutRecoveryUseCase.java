package com.impati.commerce.order.application.port.in;

public interface CheckoutRecoveryUseCase {
    int recover(int batchSize);

    boolean retry(String orderId);
}
