package com.impati.commerce.inventory.application;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {
    private final InventoryRepository inventory;

    public InventoryService(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    public synchronized StockResponse addStock(String skuId, int quantity) {
        var stock = inventory.findStock(skuId).orElseGet(() -> new StockItem(skuId));
        stock.add(quantity);
        inventory.saveStock(stock);
        return stock.toResponse();
    }

    public synchronized ReservationResponse reserve(String orderId, List<ReservationLine> lines) {
        for (var line : lines) {
            var stock = getStock(line.skuId());
            if (stock.available() < line.quantity()) {
                throw DomainException.conflict("insufficient stock for " + line.skuId());
            }
        }
        for (var line : lines) {
            var stock = getStock(line.skuId());
            stock.reserve(line.quantity());
            inventory.saveStock(stock);
        }
        var reservation = new Reservation(orderId, lines);
        inventory.saveReservation(reservation);
        return reservation.toResponse();
    }

    public synchronized ReservationResponse commit(String reservationId) {
        var reservation = getReservation(reservationId);
        for (var line : reservation.lines()) {
            var stock = getStock(line.skuId());
            stock.commit(line.quantity());
            inventory.saveStock(stock);
        }
        reservation.commit();
        inventory.saveReservation(reservation);
        return reservation.toResponse();
    }

    public synchronized ReservationResponse release(String reservationId) {
        var reservation = getReservation(reservationId);
        for (var line : reservation.lines()) {
            var stock = getStock(line.skuId());
            stock.release(line.quantity());
            inventory.saveStock(stock);
        }
        reservation.release();
        inventory.saveReservation(reservation);
        return reservation.toResponse();
    }

    public List<StockResponse> stock() {
        return inventory.stock().stream().map(item -> item.toResponse()).toList();
    }

    private StockItem getStock(String skuId) {
        return inventory.findStock(skuId)
                .orElseThrow(() -> DomainException.notFound("stock not found for " + skuId));
    }

    private Reservation getReservation(String reservationId) {
        return inventory.findReservation(reservationId)
                .orElseThrow(() -> DomainException.notFound("reservation not found"));
    }
}
