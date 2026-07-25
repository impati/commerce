package com.impati.commerce.inventory.application;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.inventory.adapter.out.persistence.InMemoryInventoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryServiceTest {
    @Test
    void reservesCommitsAndReleasesStock() {
        var inventory = new InventoryService(new InMemoryInventoryRepository());
        inventory.addStock("sku_tee_white_m", 20);

        var reservation = inventory.reserve(
                "ord_demo",
                List.of(new ReservationLine("sku_tee_white_m", 2))
        );
        assertThat(inventory.stock().getFirst().reserved()).isEqualTo(2);

        inventory.commit(reservation.id());
        var committedStock = inventory.stock().getFirst();
        assertThat(committedStock.onHand()).isEqualTo(18);
        assertThat(committedStock.reserved()).isZero();

        var secondReservation = inventory.reserve(
                "ord_retry",
                List.of(new ReservationLine("sku_tee_white_m", 1))
        );
        inventory.release(secondReservation.id());

        var releasedStock = inventory.stock().getFirst();
        assertThat(releasedStock.onHand()).isEqualTo(18);
        assertThat(releasedStock.reserved()).isZero();
    }
}

