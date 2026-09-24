package com.impati.commerce.shipping.application.port.in;

/** 응답 유실이나 프로세스 종료로 끝나지 않은 택배사 접수·취소 작업을 재개한다. */
public interface CarrierOperationRecoveryUseCase {
    int recoverPendingOperations(int batchSize);
}
