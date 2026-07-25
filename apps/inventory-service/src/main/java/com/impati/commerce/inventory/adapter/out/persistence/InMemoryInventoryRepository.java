package com.impati.commerce.inventory.adapter.out.persistence;

import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryInventoryRepository {
    private final Map<String, StockItem> stock = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();

    public StockItem getOrCreateStock(String skuId) {
        return stock.computeIfAbsent(skuId, StockItem::new);
    }

    public Optional<StockItem> findStock(String skuId) {
        return Optional.ofNullable(stock.get(skuId));
    }

    public Collection<StockItem> stock() {
        return stock.values();
    }

    public void saveReservation(Reservation reservation) {
        reservations.put(reservation.id(), reservation);
    }

    public Optional<Reservation> findReservation(String reservationId) {
        return Optional.ofNullable(reservations.get(reservationId));
    }
}

