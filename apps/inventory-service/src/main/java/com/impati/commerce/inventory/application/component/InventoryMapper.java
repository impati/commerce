package com.impati.commerce.inventory.application.component;

import com.impati.commerce.inventory.application.port.in.ReservationDetails;
import com.impati.commerce.inventory.application.port.in.StockDetails;
import com.impati.commerce.inventory.application.port.in.StockLine;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;

/**
 * 도메인 모델을 유스케이스 결과로 옮긴다. 도메인은 결과 타입을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 볼 수 있으면 도메인 객체를 손에 넣어야 부를 수
 * 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 */
final class InventoryMapper {
    private InventoryMapper() {
    }

    static StockDetails toDetails(StockItem stock) {
        return new StockDetails(stock.skuId(), stock.onHand(), stock.reserved(), stock.available());
    }

    static ReservationDetails toDetails(Reservation reservation) {
        return new ReservationDetails(
                reservation.id(),
                reservation.orderId(),
                reservation.status().name(),
                reservation.lines().stream()
                        .map(line -> new StockLine(line.skuId(), line.quantity()))
                        .toList()
        );
    }
}
