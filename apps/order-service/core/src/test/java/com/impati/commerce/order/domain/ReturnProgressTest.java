package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.domain.OrderModels.Address;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnProgressTest {
    private static final Address ADDRESS = new Address(
            "adr", "home", "고객", "010", "서울", "서울", "01234", true);

    @Test
    void lateCarrierConflictDoesNotReactivateAWithdrawnReturn() {
        var progress = progress();
        progress.pickupScheduled("rsh");
        progress.requestWithdrawal();
        progress.withdrawn();

        progress.attention();

        assertThat(progress.status()).isEqualTo(ReturnProgress.Status.WITHDRAWN);
    }

    @Test
    void receiptStartsRefundWhenPickupEventWasLost() {
        var progress = progress();
        progress.pickupScheduled("rsh");

        progress.received(OffsetDateTime.parse("2026-09-25T00:00:00Z"));

        assertThat(progress.refundStatus()).isEqualTo(ReturnProgress.WorkStatus.PENDING);
        assertThat(progress.inspectionDueAt()).isEqualTo(OffsetDateTime.parse("2026-09-28T00:00:00Z"));
    }

    @Test
    void inspectionRequiresAConditionBeforeInventoryProcessing() {
        var progress = progress();
        progress.received(OffsetDateTime.parse("2026-09-25T00:00:00Z"));

        assertThatThrownBy(() -> progress.inspect("NON_SALEABLE", " "))
                .isInstanceOf(com.impati.commerce.common.DomainException.class);
    }

    @Test
    void firstInspectionDecisionWinsButItsRetryIsIdempotent() {
        var progress = progress();
        progress.received(OffsetDateTime.parse("2026-09-25T00:00:00Z"));
        progress.inspect("NON_SALEABLE", "CUSTOMER_DAMAGED");

        progress.inspect("NON_SALEABLE", "CUSTOMER_DAMAGED");
        assertThatThrownBy(() -> progress.inspect("SALEABLE", "NORMAL"))
                .isInstanceOf(com.impati.commerce.common.DomainException.class);
        assertThat(progress.disposition()).isEqualTo("NON_SALEABLE");
    }

    private static ReturnProgress progress() {
        return new ReturnProgress("ord", "mem", "CHANGE_OF_MIND", null, null,
                Money.krw(10_000), ADDRESS, OffsetDateTime.parse("2026-09-24T00:00:00Z"));
    }
}
