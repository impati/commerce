package com.impati.commerce.inventory.adapter.out.persistence;

import com.impati.commerce.inventory.application.InventoryRepository;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryInventoryRepository implements InventoryRepository {
    private final Map<String, StockItem> stock = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();

    @Override
    public Optional<StockItem> findStock(String skuId) {
        return Optional.ofNullable(stock.get(skuId));
    }

    @Override
    public void saveStock(StockItem item) {
        stock.put(item.skuId(), item);
    }

    @Override
    public Collection<StockItem> stock() {
        return List.copyOf(stock.values());
    }

    @Override
    public void saveReservation(Reservation reservation) {
        reservations.put(reservation.id(), reservation);
    }

    @Override
    public Optional<Reservation> findReservation(String reservationId) {
        return Optional.ofNullable(reservations.get(reservationId));
    }
}
