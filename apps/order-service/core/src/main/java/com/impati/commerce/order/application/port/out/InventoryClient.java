package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.ApiContracts.ReturnInventoryResponse;
import java.util.Optional;

/** inventory-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface InventoryClient {
    ReservationResponse reserve(ReserveInventoryRequest request);

    void commitReservation(String reservationId);

    void releaseReservation(String reservationId);

    void restoreReservation(String reservationId);

    Optional<ReservationResponse> reservationForOrder(String orderId);

    ReturnInventoryResponse processReturn(String reservationId, String returnId, String memberId,
            String disposition, String condition);

    Optional<ReturnInventoryResponse> returnInventory(String returnId);
}
