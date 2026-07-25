package com.impati.commerce.inventory.application;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테스트가 같은 DB를 공유하므로 SKU id를 테스트별로 다르게 만든다. 빈 저장소를 가정하지 않는다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:inventory-app;DB_CLOSE_DELAY=-1")
class InventoryServiceTest {
    @Autowired
    private InventoryService inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reservesCommitsAndReleasesStock() {
        var skuId = "sku_lifecycle";
        inventory.addStock(skuId, 20);

        var reservation = inventory.reserve("ord_demo", List.of(new ReservationLine(skuId, 2)));
        assertThat(stockOf(skuId).reserved()).isEqualTo(2);

        inventory.commit(reservation.id());
        assertThat(stockOf(skuId).onHand()).isEqualTo(18);
        assertThat(stockOf(skuId).reserved()).isZero();

        var second = inventory.reserve("ord_retry", List.of(new ReservationLine(skuId, 1)));
        inventory.release(second.id());
        assertThat(stockOf(skuId).onHand()).isEqualTo(18);
        assertThat(stockOf(skuId).reserved()).isZero();
    }

    @Test
    void rejectsReservationBeyondAvailableStock() {
        var skuId = "sku_insufficient";
        inventory.addStock(skuId, 3);

        assertThatThrownBy(() -> inventory.reserve("ord_over", List.of(new ReservationLine(skuId, 4))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("insufficient stock");

        assertThat(stockOf(skuId).reserved()).isZero();
    }

    @Test
    void rejectsReservationForUnknownSku() {
        assertThatThrownBy(() -> inventory.reserve("ord_unknown", List.of(new ReservationLine("sku_absent", 1))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("stock not found");
    }

    /**
     * 재고가 음수로 저장되는 것은 DB 제약이 막는다. 락과 별개인 최후 방어선이며, 애플리케이션 검증을
     * 우회해 직접 update를 걸어 확인한다.
     */
    @Test
    void databaseRejectsNegativeStock() {
        var skuId = "sku_constraint";
        inventory.addStock(skuId, 1);

        assertThatThrownBy(() -> jdbc.update("update stock_items set on_hand = -1 where sku_id = ?", skuId))
                .hasMessageContaining("CK_STOCK_ITEMS_NON_NEGATIVE");
        assertThatThrownBy(() -> jdbc.update("update stock_items set reserved = 5 where sku_id = ?", skuId))
                .hasMessageContaining("CK_STOCK_ITEMS_NON_NEGATIVE");
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachFieldToItsOwnColumn() {
        var skuId = "sku_column";
        inventory.addStock(skuId, 10);
        var reservation = inventory.reserve("ord_column", List.of(new ReservationLine(skuId, 4)));

        var stockRow = jdbc.queryForMap("select on_hand, reserved from stock_items where sku_id = ?", skuId);
        assertThat(stockRow.get("ON_HAND")).isEqualTo(10);
        assertThat(stockRow.get("RESERVED")).isEqualTo(4);

        var lineRow = jdbc.queryForMap(
                "select sku_id, line_no, quantity from reservation_lines where reservation_id = ?",
                reservation.id()
        );
        assertThat(lineRow.get("SKU_ID")).isEqualTo(skuId);
        assertThat(lineRow.get("LINE_NO")).isEqualTo(0);
        assertThat(lineRow.get("QUANTITY")).isEqualTo(4);

        assertThat(jdbc.queryForObject(
                "select status from reservations where id = ?", String.class, reservation.id()))
                .isEqualTo("RESERVED");
    }

    private StockResponse stockOf(String skuId) {
        return inventory.stock().stream()
                .filter(stock -> stock.skuId().equals(skuId))
                .findFirst()
                .orElseThrow();
    }
}
