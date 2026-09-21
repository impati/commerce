package com.impati.commerce.inventory.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.inventory.application.port.in.InventoryUseCase;
import com.impati.commerce.inventory.application.port.in.ReservationDetails;
import com.impati.commerce.inventory.application.port.in.StockDetails;
import com.impati.commerce.inventory.application.port.in.StockLine;
import com.impati.commerce.test.RequiresDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테스트가 같은 DB를 공유하므로 SKU id를 테스트별로 다르게 만든다. 빈 저장소를 가정하지 않는다.
 */
@SpringBootTest
@RequiresDatabase
class InventoryExecutorTest {

    @Autowired
    private InventoryUseCase inventoryUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * [PD-0005-R1][PD-0005-R4] 가용 수량 계산과 확정·해제가 각각 무엇을 줄이는지 잡는다.
     */
    @Test
    void reservesCommitsAndReleasesStock() {
        var skuId = "sku_lifecycle";
        inventoryUseCase.addStock(skuId, 20);

        var reservation = inventoryUseCase.reserve("ord_demo", List.of(new StockLine(skuId, 2)));
        assertThat(stockOf(skuId).reserved()).isEqualTo(2);

        inventoryUseCase.commit(reservation.id());
        assertThat(stockOf(skuId).onHand()).isEqualTo(18);
        assertThat(stockOf(skuId).reserved()).isZero();

        var second = inventoryUseCase.reserve("ord_retry", List.of(new StockLine(skuId, 1)));
        inventoryUseCase.release(second.id());
        assertThat(stockOf(skuId).onHand()).isEqualTo(18);
        assertThat(stockOf(skuId).reserved()).isZero();
    }

    @Test
    void repeatingAReservationAndItsCommitDoesNotChangeStockTwice() {
        var skuId = "sku_idem_reservation";
        inventoryUseCase.addStock(skuId, 10);

        var first = inventoryUseCase.reserve("ord_idem_reservation", List.of(new StockLine(skuId, 2)));
        var second = inventoryUseCase.reserve("ord_idem_reservation", List.of(new StockLine(skuId, 2)));
        inventoryUseCase.commit(first.id());
        inventoryUseCase.commit(first.id());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(stockOf(skuId).onHand()).isEqualTo(8);
        assertThat(stockOf(skuId).reserved()).isZero();
        assertThat(movementCount("RESERVATION_CREATED", first.id())).isOne();
        assertThat(movementCount("RESERVATION_COMMITTED", first.id())).isOne();
    }

    /** [PD-0024-R6][PD-0024-R7] 확정 재고 복원은 전체 수량을 정확히 한 번 되돌린다. */
    @Test
    void restoresCommittedReservationOnlyOnce() {
        var skuId = "sku_restore_committed";
        inventoryUseCase.addStock(skuId, 10);
        var reservation = inventoryUseCase.reserve("ord_restore_committed", List.of(new StockLine(skuId, 3)));
        inventoryUseCase.commit(reservation.id());

        var first = inventoryUseCase.restore(reservation.id());
        var second = inventoryUseCase.restore(reservation.id());

        assertThat(first.status()).isEqualTo("RESTORED");
        assertThat(second).isEqualTo(first);
        assertThat(stockOf(skuId).onHand()).isEqualTo(10);
        assertThat(stockOf(skuId).reserved()).isZero();
        assertThat(movementCount("RESERVATION_RESTORED", reservation.id())).isOne();
    }

    /** [PD-0024-R6] 아직 확정되지 않았거나 해제된 예약은 판매 재고 복원 대상이 아니다. */
    @Test
    void rejectsRestoreUnlessReservationWasCommitted() {
        var skuId = "sku_restore_rejected";
        inventoryUseCase.addStock(skuId, 10);
        var reserved = inventoryUseCase.reserve("ord_restore_reserved", List.of(new StockLine(skuId, 2)));

        assertThatThrownBy(() -> inventoryUseCase.restore(reserved.id()))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("conflict"));

        var released = inventoryUseCase.reserve("ord_restore_released", List.of(new StockLine(skuId, 2)));
        inventoryUseCase.release(released.id());
        assertThatThrownBy(() -> inventoryUseCase.restore(released.id()))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("conflict"));
    }

    @Test
    void repeatingAReservationWithDifferentLinesIsRejected() {
        var skuId = "sku_changed_reservation";
        inventoryUseCase.addStock(skuId, 10);
        inventoryUseCase.reserve("ord_changed_reservation", List.of(new StockLine(skuId, 2)));

        assertThatThrownBy(() -> inventoryUseCase.reserve(
                "ord_changed_reservation", List.of(new StockLine(skuId, 3))))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("conflict"));
        assertThat(stockOf(skuId).reserved()).isEqualTo(2);
    }

    /**
     * [PD-0005-R2] 가용 수량을 넘는 예약이 거절되는 것을 잡는다. 여러 줄 중 하나만 모자란 경우는 보지 않는다.
     */
    @Test
    void rejectsReservationBeyondAvailableStock() {
        var skuId = "sku_insufficient";
        inventoryUseCase.addStock(skuId, 3);

        assertThatThrownBy(() -> inventoryUseCase.reserve("ord_over", List.of(new StockLine(skuId, 4))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("insufficient stock");

        assertThat(stockOf(skuId).reserved()).isZero();
    }

    /**
     * [PD-0005-R3] 등록되지 않은 상품은 예약할 수 없다. 예약 시도가 재고를 만들지 않는다.
     */
    @Test
    void rejectsReservationForUnknownSku() {
        assertThatThrownBy(() -> inventoryUseCase.reserve("ord_unknown", List.of(new StockLine("sku_absent", 1))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("stock not found");
    }

    /**
     * [PD-0005-R7] 재고가 음수로 저장되는 것은 DB 제약이 막는다. 락과 별개인 최후 방어선이며,
     * 애플리케이션 검증을 우회해 직접 update를 걸어 확인한다.
     */
    @Test
    void databaseRejectsNegativeStock() {
        var skuId = "sku_constraint";
        inventoryUseCase.addStock(skuId, 1);

        assertThatThrownBy(() -> jdbc.update("update stock_items set on_hand = -1 where sku_id = ?", skuId))
                .hasMessageContaining("ck_stock_items_non_negative");
        assertThatThrownBy(() -> jdbc.update("update stock_items set reserved = 5 where sku_id = ?", skuId))
                .hasMessageContaining("ck_stock_items_non_negative");
    }

    /**
     * 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다.
     */
    @Test
    void writesEachFieldToItsOwnColumn() {
        var skuId = "sku_column";
        inventoryUseCase.addStock(skuId, 10);
        var reservation = inventoryUseCase.reserve("ord_column", List.of(new StockLine(skuId, 4)));

        var stockRow = jdbc.queryForMap("select on_hand, reserved from stock_items where sku_id = ?", skuId);
        assertThat(stockRow.get("on_hand")).isEqualTo(10);
        assertThat(stockRow.get("reserved")).isEqualTo(4);

        var lineRow = jdbc.queryForMap(
                "select sku_id, line_no, quantity from reservation_lines where reservation_id = ?",
                reservation.id()
        );
        assertThat(lineRow.get("sku_id")).isEqualTo(skuId);
        assertThat(lineRow.get("line_no")).isEqualTo(0);
        assertThat(lineRow.get("quantity")).isEqualTo(4);

        assertThat(jdbc.queryForObject(
                "select status from reservations where id = ?", String.class, reservation.id()))
                .isEqualTo("RESERVED");
    }

    @Test
    void commitInConcurrency() throws InterruptedException, ExecutionException {
        // given
        var skuId = "sku_lifecycle2";
        inventoryUseCase.addStock(skuId, 20);

        var reservation = inventoryUseCase.reserve("ord_commited", List.of(new StockLine(skuId, 2)));
        assertThat(stockOf(skuId).reserved()).isEqualTo(2);

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch countDownLatch = new CountDownLatch(20);
        List<Future<?>> futures = new ArrayList<>();
        // when & then
        try {
            for (int i = 0; i < 20; i++) {
                Future<?> future = executorService.submit(() -> {
                    try {
                        ReservationDetails committed = inventoryUseCase.commit(reservation.id());
                        assertThat(committed.id()).isEqualTo(reservation.id());
                    } finally {
                        countDownLatch.countDown();
                    }
                });
                futures.add(future);
            }
            assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();

            for (Future<?> future : futures) {
                future.get();
            }

            assertThat(stockOf(skuId).onHand()).isEqualTo(18);
            assertThat(stockOf(skuId).reserved()).isZero();
            assertThat(movementCount("RESERVATION_COMMITTED", reservation.id())).isOne();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void releaseInConcurrency() throws InterruptedException, ExecutionException {
        // given
        var skuId = "sku_lifecycle3";
        inventoryUseCase.addStock(skuId, 20);

        var reservation = inventoryUseCase.reserve("ord_release", List.of(new StockLine(skuId, 2)));
        assertThat(stockOf(skuId).reserved()).isEqualTo(2);

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch countDownLatch = new CountDownLatch(20);
        List<Future<?>> futures = new ArrayList<>();
        // when & then
        try {
            for (int i = 0; i < 20; i++) {
                Future<?> future = executorService.submit(() -> {
                    try {
                        ReservationDetails released = inventoryUseCase.release(reservation.id());
                        assertThat(released.id()).isEqualTo(reservation.id());
                    } finally {
                        countDownLatch.countDown();
                    }
                });
                futures.add(future);
            }
            assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();

            for (Future<?> future : futures) {
                future.get();
            }
            assertThat(stockOf(skuId).onHand()).isEqualTo(20);
            assertThat(stockOf(skuId).reserved()).isZero();
            assertThat(movementCount("RESERVATION_RELEASED", reservation.id())).isOne();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void restoreInConcurrency() throws InterruptedException, ExecutionException {
        var skuId = "sku_restore_concurrency";
        inventoryUseCase.addStock(skuId, 20);
        var reservation = inventoryUseCase.reserve(
                "ord_restore_concurrency", List.of(new StockLine(skuId, 2)));
        inventoryUseCase.commit(reservation.id());

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch countDownLatch = new CountDownLatch(20);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                futures.add(executorService.submit(() -> {
                    try {
                        ReservationDetails restored = inventoryUseCase.restore(reservation.id());
                        assertThat(restored.id()).isEqualTo(reservation.id());
                    } finally {
                        countDownLatch.countDown();
                    }
                }));
            }
            assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();
            for (Future<?> future : futures) {
                future.get();
            }

            assertThat(stockOf(skuId).onHand()).isEqualTo(20);
            assertThat(stockOf(skuId).reserved()).isZero();
            assertThat(movementCount("RESERVATION_RESTORED", reservation.id())).isOne();
        } finally {
            executorService.shutdown();
        }
    }

    private StockDetails stockOf(String skuId) {
        return inventoryUseCase.stock().stream()
                .filter(stock -> stock.skuId().equals(skuId))
                .findFirst()
                .orElseThrow();
    }

    private int movementCount(String reason, String reservationId) {
        return jdbc.queryForObject("""
                        select count(*)
                          from inventory_movements
                         where reason = ? and reservation_id = ?
                        """,
                Integer.class,
                reason,
                reservationId
        );
    }

    @Test
    void targetedStockLookupReadsOnlyExistingInventory() {
        inventoryUseCase.addStock("sku_targeted_lookup", 7);
        assertThat(inventoryUseCase.getStock("sku_targeted_lookup").available()).isEqualTo(7);
        assertThatThrownBy(() -> inventoryUseCase.getStock("sku_targeted_missing"))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("not_found"));
        assertThat(jdbc.queryForObject("select count(*) from stock_items where sku_id = ?", Integer.class, "sku_targeted_missing")).isZero();
    }
}
