package com.impati.commerce.inventory.application;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.inventory.adapter.out.persistence.InMemoryInventoryRepository;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {
    private final InMemoryInventoryRepository inventory;

    public InventoryService(InMemoryInventoryRepository inventory) {
        this.inventory = inventory;
    }

    public synchronized StockResponse addStock(String skuId, int quantity) {
        var stock = inventory.getOrCreateStock(skuId);
        stock.add(quantity);
        return stock.toResponse();
    }

    public synchronized ReservationResponse reserve(String orderId, List<ReservationLine> lines) {
        for (var line : lines) {
            var stock = inventory.findStock(line.skuId())
                    .orElseThrow(() -> DomainException.notFound("stock not found for " + line.skuId()));
            if (stock.available() < line.quantity()) {
                throw DomainException.conflict("insufficient stock for " + line.skuId());
            }
        }
        for (var line : lines) {
            inventory.findStock(line.skuId()).orElseThrow().reserve(line.quantity());
        }
        var reservation = new Reservation(orderId, lines);
        inventory.saveReservation(reservation);
        return reservation.toResponse();
    }

    public synchronized ReservationResponse commit(String reservationId) {
        var reservation = getReservation(reservationId);
        reservation.lines().forEach(line -> inventory.findStock(line.skuId()).orElseThrow().commit(line.quantity()));
        reservation.commit();
        inventory.saveReservation(reservation);
        return reservation.toResponse();
    }

    public synchronized ReservationResponse release(String reservationId) {
        var reservation = getReservation(reservationId);
        reservation.lines().forEach(line -> inventory.findStock(line.skuId()).orElseThrow().release(line.quantity()));
        reservation.release();
        inventory.saveReservation(reservation);
        return reservation.toResponse();
    }

    public List<StockResponse> stock() {
        return inventory.stock().stream().map(item -> item.toResponse()).toList();
    }

    private Reservation getReservation(String reservationId) {
        return inventory.findReservation(reservationId)
                .orElseThrow(() -> DomainException.notFound("reservation not found"));
    }
}

