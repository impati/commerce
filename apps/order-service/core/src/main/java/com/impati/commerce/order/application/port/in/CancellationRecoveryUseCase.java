package com.impati.commerce.order.application.port.in;

public interface CancellationRecoveryUseCase {
    int recover(int batchSize);

    boolean retry(String orderId);
}
