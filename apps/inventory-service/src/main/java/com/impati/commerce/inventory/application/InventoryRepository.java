package com.impati.commerce.inventory.application;

import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;

import java.util.Collection;
import java.util.Optional;

/**
 * 재고 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>조회로 얻은 {@link StockItem}을 변형한 것만으로 저장됐다고 가정하지 말 것.
 * 변경했으면 반드시 {@link #saveStock}을 부른다 — 구현이 조회마다 새 객체를 돌려줄 수 있다.
 */
public interface InventoryRepository {
    Optional<StockItem> findStock(String skuId);

    void saveStock(StockItem stock);

    Collection<StockItem> stock();

    void saveReservation(Reservation reservation);

    Optional<Reservation> findReservation(String reservationId);
}
