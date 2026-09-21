package com.impati.commerce.inventory.application.component;

import com.impati.commerce.inventory.application.port.in.InventoryUseCase;
import com.impati.commerce.inventory.application.port.in.StockLine;
import com.impati.commerce.inventory.application.port.out.InventoryRepository;
import com.impati.commerce.inventory.domain.InventoryModels.MovementReason;
import com.impati.commerce.test.RequiresDatabase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@RequiresDatabase
class InventoryMovementRollbackTest {

    @Autowired
    private InventoryUseCase inventoryUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private InventoryRepository inventoryRepository;

    @Test
    void rollsBackStockAndReservationWhenMovementCannotBeSaved() {
        var skuId = "sku_movement_rollback";
        var orderId = "ord_movement_rollback";
        inventoryUseCase.addStock(skuId, 10);
        doThrow(new IllegalStateException("movement store unavailable"))
                .when(inventoryRepository)
                .saveMovement(argThat(movement -> movement.reason() == MovementReason.RESERVATION_CREATED));

        assertThatThrownBy(() -> inventoryUseCase.reserve(orderId, List.of(new StockLine(skuId, 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("movement store unavailable");

        assertThat(inventoryUseCase.getStock(skuId).reserved()).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from reservations where order_id = ?", Integer.class, orderId
        )).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from inventory_movements where order_id = ?", Integer.class, orderId
        )).isZero();
    }
}
