package com.impati.commerce.inventory.application.port.out;

import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 재고 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>조회로 얻은 {@link StockItem}을 변형한 것만으로 저장됐다고 가정하지 말 것.
 * 변경했으면 반드시 {@link #saveStock}을 부른다 — 구현이 조회마다 새 객체를 돌려줄 수 있다.
 */
public interface InventoryRepository {
    Optional<StockItem> findStock(String skuId);

    /**
     * 재고를 잠근 채로 읽는다. 재고를 변경할 의도가 있을 때만 쓴다.
     *
     * <p>여러 프로세스가 같은 SKU를 동시에 예약하면 read-modify-write 사이에 끼어들어 초과
     * 판매가 생긴다. 잠금 없이 읽고 쓰는 경로에는 이 메서드를 쓴다.
     *
     * @param skuIds 잠글 SKU. 데드락을 피하려고 구현이 정렬한 순서로 잠근다.
     */
    List<StockItem> lockStock(Collection<String> skuIds);

    void saveStock(StockItem stock);

    Collection<StockItem> stock();

    void saveReservation(Reservation reservation);

    Optional<Reservation> findReservation(String reservationId);

    Optional<Reservation> findReservationByOrderId(String orderId);
}
