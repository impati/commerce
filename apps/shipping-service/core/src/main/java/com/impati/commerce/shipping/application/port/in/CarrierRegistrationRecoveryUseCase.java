package com.impati.commerce.shipping.application.port.in;

public interface CarrierRegistrationRecoveryUseCase {
    int recoverPendingRegistrations(int batchSize);
}
