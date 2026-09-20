package com.impati.commerce.inventory.domain;

import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.ReservationStatus;
import com.impati.commerce.inventory.domain.InventoryModels.ReservedLine;
import com.impati.commerce.inventory.domain.InventoryModels.TransitionOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryModelsTest {

    @Test
    void reservationOwnsItsIdempotentCommitAndRestoreTransitions() {
        var reservation = reservation();

        assertThat(reservation.commit()).isEqualTo(TransitionOutcome.APPLIED);
        assertThat(reservation.commit()).isEqualTo(TransitionOutcome.UNCHANGED);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.COMMITTED);

        assertThat(reservation.restore()).isEqualTo(TransitionOutcome.APPLIED);
        assertThat(reservation.restore()).isEqualTo(TransitionOutcome.UNCHANGED);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESTORED);
    }

    @Test
    void reservationRejectsTransitionsThatDoNotStartFromReserved() {
        var reservation = reservation();
        reservation.commit();

        assertThatThrownBy(reservation::release)
                .hasMessage("reservation is not reserved");
    }

    @Test
    void reservationRejectsRestoreBeforeCommit() {
        assertThatThrownBy(reservation()::restore)
                .hasMessage("only committed reservation can be restored");
    }

    private Reservation reservation() {
        return new Reservation("ord_domain", List.of(new ReservedLine("sku_domain", 2)));
    }
}
