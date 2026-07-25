package com.impati.commerce.inventory.application;

import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;

import java.util.Collection;
import java.util.Optional;

/**
 * 재고 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface InventoryRepository {
    /**
     * 없으면 재고 0인 항목을 만들어 돌려준다. 조회만으로 행이 생기는 셈이라
     * DB 구현에서는 findStock + 애플리케이션 기본값으로 나눠야 한다.
     */
    StockItem getOrCreateStock(String skuId);

    Optional<StockItem> findStock(String skuId);

    Collection<StockItem> stock();

    void saveReservation(Reservation reservation);

    Optional<Reservation> findReservation(String reservationId);
}
