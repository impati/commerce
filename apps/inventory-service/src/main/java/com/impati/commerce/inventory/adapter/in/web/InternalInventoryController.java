package com.impati.commerce.inventory.adapter.in.web;

import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.ApiContracts.StockIncreaseRequest;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.inventory.application.port.in.InventoryUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게이트웨이가 노출하지 않는 경로. 형제 서비스와 운영이 부른다 (ADR-0003).
 *
 * <p>예약·확정·해제는 order-service의 checkout saga가 부른다. 사용자가 직접 부를 수 있으면
 * 재고를 임의로 묶거나 남의 예약을 해제할 수 있다.
 *
 * <p>재입고는 운영 동작이다. 지금은 부르는 코드가 없고 수동 호출로만 쓰인다.
 */
@RestController
@RequestMapping("/internal")
public class InternalInventoryController {
    private final InventoryUseCase inventoryUseCase;

    public InternalInventoryController(InventoryUseCase inventoryUseCase) {
        this.inventoryUseCase = inventoryUseCase;
    }

    @PostMapping("/stock")
    StockResponse addStock(@RequestBody StockIncreaseRequest request) {
        return InventoryResponseMapper.from(inventoryUseCase.addStock(request.skuId(), request.quantity()));
    }

    @PostMapping("/reservations")
    ReservationResponse reserve(@RequestBody ReserveInventoryRequest request) {
        return InventoryResponseMapper.from(
                inventoryUseCase.reserve(request.orderId(), InventoryResponseMapper.toLines(request.lines())));
    }

    @PostMapping("/reservations/{reservationId}/commit")
    ReservationResponse commit(@PathVariable String reservationId) {
        return InventoryResponseMapper.from(inventoryUseCase.commit(reservationId));
    }

    @PostMapping("/reservations/{reservationId}/release")
    ReservationResponse release(@PathVariable String reservationId) {
        return InventoryResponseMapper.from(inventoryUseCase.release(reservationId));
    }

    @GetMapping("/reservations/orders/{orderId}")
    ReservationResponse reservationForOrder(@PathVariable String orderId) {
        return InventoryResponseMapper.from(inventoryUseCase.reservationForOrder(orderId));
    }
}
