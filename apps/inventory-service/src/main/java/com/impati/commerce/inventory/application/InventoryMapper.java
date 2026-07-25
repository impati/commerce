package com.impati.commerce.inventory.application;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다. 도메인은 계약을 모른다.
 */
final class InventoryMapper {
    private InventoryMapper() {
    }

    static StockResponse toResponse(StockItem stock) {
        return new StockResponse(stock.skuId(), stock.onHand(), stock.reserved(), stock.available());
    }

    static ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.id(),
                reservation.orderId(),
                reservation.status(),
                reservation.lines().stream()
                        .map(line -> new ReservationLine(line.skuId(), line.quantity()))
                        .toList()
        );
    }
}
