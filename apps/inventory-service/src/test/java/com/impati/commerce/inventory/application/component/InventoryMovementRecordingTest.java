package com.impati.commerce.inventory.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.inventory.application.port.in.InventoryUseCase;
import com.impati.commerce.inventory.application.port.in.StockLine;
import com.impati.commerce.test.RequiresDatabase;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@RequiresDatabase
class InventoryMovementRecordingTest {

    @Autowired
    private InventoryUseCase inventoryUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recordsEveryCurrentMovementWithItsResultingBalance() {
        var skuId = "sku_movement_lifecycle";
        inventoryUseCase.addStock(skuId, 10);

        var increased = movementForSku("STOCK_INCREASED", skuId);
        assertThat(increased.orderId()).isNull();
        assertThat(increased.reservationId()).isNull();
        assertThat(increased.occurredAt()).isNotNull();
        assertThat(line(increased.id(), skuId)).isEqualTo(new MovementLineRow(10, 0, 10, 0));

        var committedReservation = inventoryUseCase.reserve(
                "ord_movement_committed", List.of(new StockLine(skuId, 3)));
        var reserved = movement("RESERVATION_CREATED", committedReservation.id());
        assertThat(reserved.orderId()).isEqualTo("ord_movement_committed");
        assertThat(line(reserved.id(), skuId)).isEqualTo(new MovementLineRow(0, 3, 10, 3));

        inventoryUseCase.commit(committedReservation.id());
        var committed = movement("RESERVATION_COMMITTED", committedReservation.id());
        assertThat(line(committed.id(), skuId)).isEqualTo(new MovementLineRow(-3, -3, 7, 0));

        inventoryUseCase.restore(committedReservation.id());
        var restored = movement("RESERVATION_RESTORED", committedReservation.id());
        assertThat(line(restored.id(), skuId)).isEqualTo(new MovementLineRow(3, 0, 10, 0));

        var releasedReservation = inventoryUseCase.reserve(
                "ord_movement_released", List.of(new StockLine(skuId, 2)));
        inventoryUseCase.release(releasedReservation.id());
        var released = movement("RESERVATION_RELEASED", releasedReservation.id());
        assertThat(line(released.id(), skuId)).isEqualTo(new MovementLineRow(0, -2, 10, 0));
    }

    @Test
    void groupsEverySkuOfAReservationUnderOneMovement() {
        var firstSku = "sku_movement_group_a";
        var secondSku = "sku_movement_group_b";
        inventoryUseCase.addStock(firstSku, 10);
        inventoryUseCase.addStock(secondSku, 20);

        var reservation = inventoryUseCase.reserve("ord_movement_group", List.of(
                new StockLine(firstSku, 2),
                new StockLine(secondSku, 5)
        ));

        var movement = movement("RESERVATION_CREATED", reservation.id());
        assertThat(jdbc.queryForObject(
                "select count(*) from inventory_movement_lines where movement_id = ?",
                Integer.class,
                movement.id()
        )).isEqualTo(2);
        assertThat(line(movement.id(), firstSku)).isEqualTo(new MovementLineRow(0, 2, 10, 2));
        assertThat(line(movement.id(), secondSku)).isEqualTo(new MovementLineRow(0, 5, 20, 5));
    }

    @Test
    void recordsNeitherRejectedRequestsNorUnchangedRetries() {
        var skuId = "sku_movement_applied_only";
        inventoryUseCase.addStock(skuId, 5);
        var reservation = inventoryUseCase.reserve(
                "ord_movement_applied_only", List.of(new StockLine(skuId, 2)));

        inventoryUseCase.commit(reservation.id());
        inventoryUseCase.commit(reservation.id());
        inventoryUseCase.restore(reservation.id());
        inventoryUseCase.restore(reservation.id());

        assertThat(movementCount("RESERVATION_COMMITTED", reservation.id())).isOne();
        assertThat(movementCount("RESERVATION_RESTORED", reservation.id())).isOne();

        assertThatThrownBy(() -> inventoryUseCase.reserve(
                "ord_movement_rejected", List.of(new StockLine(skuId, 6))))
                .isInstanceOf(DomainException.class);
        assertThat(jdbc.queryForObject(
                "select count(*) from inventory_movements where order_id = ?",
                Integer.class,
                "ord_movement_rejected"
        )).isZero();
    }

    private MovementRow movementForSku(String reason, String skuId) {
        return jdbc.queryForObject("""
                        select m.id, m.order_id, m.reservation_id, m.occurred_at
                          from inventory_movements m
                          join inventory_movement_lines l on l.movement_id = m.id
                         where m.reason = ? and l.sku_id = ?
                        """,
                (rs, rowNum) -> new MovementRow(
                        rs.getString("id"),
                        rs.getString("order_id"),
                        rs.getString("reservation_id"),
                        rs.getObject("occurred_at", LocalDateTime.class)
                ),
                reason,
                skuId
        );
    }

    private MovementRow movement(String reason, String reservationId) {
        return jdbc.queryForObject("""
                        select id, order_id, reservation_id, occurred_at
                          from inventory_movements
                         where reason = ? and reservation_id = ?
                        """,
                (rs, rowNum) -> new MovementRow(
                        rs.getString("id"),
                        rs.getString("order_id"),
                        rs.getString("reservation_id"),
                        rs.getObject("occurred_at", LocalDateTime.class)
                ),
                reason,
                reservationId
        );
    }

    private MovementLineRow line(String movementId, String skuId) {
        return jdbc.queryForObject("""
                        select on_hand_delta, reserved_delta, on_hand_after, reserved_after
                          from inventory_movement_lines
                         where movement_id = ? and sku_id = ?
                        """,
                (rs, rowNum) -> new MovementLineRow(
                        rs.getInt("on_hand_delta"),
                        rs.getInt("reserved_delta"),
                        rs.getInt("on_hand_after"),
                        rs.getInt("reserved_after")
                ),
                movementId,
                skuId
        );
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

    private record MovementRow(
            String id,
            String orderId,
            String reservationId,
            LocalDateTime occurredAt
    ) {
    }

    private record MovementLineRow(
            int onHandDelta,
            int reservedDelta,
            int onHandAfter,
            int reservedAfter
    ) {
    }
}
