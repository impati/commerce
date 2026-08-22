package com.impati.commerce.inventory.adapter.in.web;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.inventory.application.port.in.ReservationDetails;
import com.impati.commerce.inventory.application.port.in.StockDetails;
import com.impati.commerce.inventory.application.port.in.StockLine;

import java.util.List;

/**
 * 서비스 간 HTTP 계약과 유스케이스 입출력을 잇는다.
 *
 * <p>계약이 <b>서비스 간</b>의 것이므로 어댑터가 안다. 응용 계층이 알면 인바운드 어댑터가
 * 늘어날 때마다 응용이 바뀐다.
 */
final class InventoryResponseMapper {
    private InventoryResponseMapper() {
    }

    static List<StockLine> toLines(List<ReservationLine> lines) {
        return lines.stream().map(line -> new StockLine(line.skuId(), line.quantity())).toList();
    }

    static StockResponse from(StockDetails stock) {
        return new StockResponse(stock.skuId(), stock.onHand(), stock.reserved(), stock.available());
    }

    static ReservationResponse from(ReservationDetails reservation) {
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
