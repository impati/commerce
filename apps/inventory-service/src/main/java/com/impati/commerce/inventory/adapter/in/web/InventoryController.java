package com.impati.commerce.inventory.adapter.in.web;

import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.ApiContracts.StockIncreaseRequest;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.inventory.application.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/stock")
    List<StockResponse> stock() {
        return inventory.stock();
    }

    @PostMapping("/stock")
    StockResponse addStock(@RequestBody StockIncreaseRequest request) {
        return inventory.addStock(request.skuId(), request.quantity());
    }

    @PostMapping("/reservations")
    ReservationResponse reserve(@RequestBody ReserveInventoryRequest request) {
        return inventory.reserve(request.orderId(), request.lines());
    }

    @PostMapping("/reservations/{reservationId}/commit")
    ReservationResponse commit(@PathVariable String reservationId) {
        return inventory.commit(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/release")
    ReservationResponse release(@PathVariable String reservationId) {
        return inventory.release(reservationId);
    }
}

