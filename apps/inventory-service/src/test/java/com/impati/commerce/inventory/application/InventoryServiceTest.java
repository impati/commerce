package com.impati.commerce.inventory.application;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.inventory.adapter.out.persistence.InMemoryInventoryRepository;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryServiceTest {
    private static final String SKU_ID = "sku_tee_white_m";

    /**
     * 재고 변경이 저장소에 반영되는지 확인한다.
     *
     * <p>일부러 {@link DetachedInventoryRepository}를 쓴다. 인메모리 어댑터는 맵 안의 객체
     * 참조를 그대로 돌려주므로, 애플리케이션이 {@code saveStock}을 부르지 않아도 변경이
     * 남아버린다. 그러면 이 테스트가 DB 어댑터에서 깨질 코드를 통과시킨다.
     */
    @Test
    void reservesCommitsAndReleasesStock() {
        var repository = new DetachedInventoryRepository();
        var inventory = new InventoryService(repository);
        inventory.addStock(SKU_ID, 20);

        var reservation = inventory.reserve(
                "ord_demo",
                List.of(new ReservationLine(SKU_ID, 2))
        );
        assertThat(inventory.stock().getFirst().reserved()).isEqualTo(2);

        inventory.commit(reservation.id());
        var committedStock = inventory.stock().getFirst();
        assertThat(committedStock.onHand()).isEqualTo(18);
        assertThat(committedStock.reserved()).isZero();

        var secondReservation = inventory.reserve(
                "ord_retry",
                List.of(new ReservationLine(SKU_ID, 1))
        );
        inventory.release(secondReservation.id());

        var releasedStock = inventory.stock().getFirst();
        assertThat(releasedStock.onHand()).isEqualTo(18);
        assertThat(releasedStock.reserved()).isZero();
    }

    @Test
    void inMemoryAdapterStoresStock() {
        var repository = new InMemoryInventoryRepository();
        var item = new StockItem(SKU_ID);
        item.add(5);

        repository.saveStock(item);

        assertThat(repository.findStock(SKU_ID)).isPresent();
        assertThat(repository.findStock(SKU_ID).orElseThrow().toResponse().onHand()).isEqualTo(5);
        assertThat(repository.findStock("sku_unknown")).isEmpty();
    }

    /**
     * DB 어댑터처럼 조회할 때마다 새 객체를 돌려주는 테스트 대역.
     * 변경 후 {@code saveStock}을 부르지 않으면 그 변경은 사라진다.
     *
     * <p>예약(Reservation)은 id를 생성자에서 만들어 복원할 수 없어 참조를 그대로 보관한다.
     * 예약 쪽은 원래부터 명시적 saveReservation을 부르고 있어 위험이 낮다.
     */
    private static final class DetachedInventoryRepository implements InventoryRepository {
        private final Map<String, StockResponse> stock = new LinkedHashMap<>();
        private final Map<String, Reservation> reservations = new LinkedHashMap<>();

        @Override
        public Optional<StockItem> findStock(String skuId) {
            return Optional.ofNullable(stock.get(skuId)).map(DetachedInventoryRepository::rebuild);
        }

        @Override
        public void saveStock(StockItem item) {
            stock.put(item.skuId(), item.toResponse());
        }

        @Override
        public Collection<StockItem> stock() {
            return stock.values().stream().map(DetachedInventoryRepository::rebuild).toList();
        }

        @Override
        public void saveReservation(Reservation reservation) {
            reservations.put(reservation.id(), reservation);
        }

        @Override
        public Optional<Reservation> findReservation(String reservationId) {
            return Optional.ofNullable(reservations.get(reservationId));
        }

        private static StockItem rebuild(StockResponse snapshot) {
            var item = new StockItem(snapshot.skuId());
            if (snapshot.onHand() > 0) {
                item.add(snapshot.onHand());
            }
            if (snapshot.reserved() > 0) {
                item.reserve(snapshot.reserved());
            }
            return item;
        }
    }
}
