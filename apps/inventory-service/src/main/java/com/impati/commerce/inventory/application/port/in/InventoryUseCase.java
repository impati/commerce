package com.impati.commerce.inventory.application.port.in;

import java.util.List;

/** 재고로 할 수 있는 일. */
public interface InventoryUseCase {
    StockDetails addStock(String skuId, int quantity);

    ReservationDetails reserve(String orderId, List<StockLine> lines);

    ReservationDetails commit(String reservationId);

    ReservationDetails release(String reservationId);

    List<StockDetails> stock();

    /** 시드가 이미 들어가 있는지 확인한다. 파일 DB에서는 재시작마다 넣으면 재고가 늘어난다. */
    boolean isEmpty();
}
