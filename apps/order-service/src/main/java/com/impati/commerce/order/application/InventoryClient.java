package com.impati.commerce.order.application;

import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;

/** inventory-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface InventoryClient {
    ReservationResponse reserve(ReserveInventoryRequest request);

    void commitReservation(String reservationId);

    void releaseReservation(String reservationId);
}
