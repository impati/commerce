package com.impati.commerce.inventory.application.port.in;

import java.util.List;

/**
 * 재고로 할 수 있는 일.
 */
public interface InventoryUseCase {

    StockDetails addStock(String skuId, int quantity);

    ReservationDetails reserve(String orderId, List<StockLine> lines);

    ReservationDetails commit(String reservationId);

    ReservationDetails release(String reservationId);

    ReservationDetails restore(String reservationId);

    ReturnInventoryDetails processReturn(
            String returnId,
            String reservationId,
            String memberId,
            String disposition,
            String condition
    );

    ReturnInventoryDetails getReturn(String returnId);

    ReservationDetails reservationForOrder(String orderId);

    List<StockDetails> stock();

    StockDetails getStock(String skuId);

    /**
     * 시드가 이미 들어가 있는지 확인한다. 파일 DB에서는 재시작마다 넣으면 재고가 늘어난다.
     */
    boolean isEmpty();
}
