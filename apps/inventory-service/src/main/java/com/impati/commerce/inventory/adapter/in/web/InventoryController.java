package com.impati.commerce.inventory.adapter.in.web;

import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.inventory.application.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
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
}
