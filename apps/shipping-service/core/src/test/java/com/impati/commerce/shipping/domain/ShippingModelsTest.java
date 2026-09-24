package com.impati.commerce.shipping.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.CarrierEventCommand;
import com.impati.commerce.shipping.domain.ShippingModels.Address;
import com.impati.commerce.shipping.domain.ShippingModels.CarrierEventType;
import com.impati.commerce.shipping.domain.ShippingModels.EventDecision;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippingModelsTest {
    @Test
    void createsReadyWithoutTrackingAndConfirmsRegistrationIdempotently() {
        var shipment = shipment();

        assertThat(shipment.status()).isEqualTo(ShippingModels.ShipmentStatus.READY);
        assertThat(shipment.trackingNumber()).isNull();

        shipment.requestRegistration();
        shipment.confirmRegistration("PRIMARY", "기본 택배사", "TRK-1");
        shipment.confirmRegistration("PRIMARY", "기본 택배사", "TRK-1");

        assertThat(shipment.status()).isEqualTo(ShippingModels.ShipmentStatus.AWAITING_PICKUP);
    }

    @Test
    void pickupClosesCancellationBoundary() {
        var shipment = registered();

        assertThat(shipment.applyCarrierEvent(CarrierEventType.PICKED_UP, time(1))).isEqualTo(EventDecision.APPLIED);
        assertThatThrownBy(shipment::requestCancellation).isInstanceOf(DomainException.class);
    }

    @Test
    void deliveryFailureAndReturnAreNormalProgress() {
        var shipment = registered();

        assertThat(shipment.applyCarrierEvent(CarrierEventType.DELIVERY_FAILED, time(1))).isEqualTo(EventDecision.APPLIED);
        assertThat(shipment.status()).isEqualTo(ShippingModels.ShipmentStatus.RETURNING);
        assertThat(shipment.applyCarrierEvent(CarrierEventType.RETURNED, time(2))).isEqualTo(EventDecision.APPLIED);
        assertThat(shipment.status()).isEqualTo(ShippingModels.ShipmentStatus.RETURNED);
    }

    @Test
    void staleAndContradictoryEventsDoNotReplaceTerminalResult() {
        var shipment = registered();
        shipment.applyCarrierEvent(CarrierEventType.DELIVERED, time(2));

        assertThat(shipment.applyCarrierEvent(CarrierEventType.PICKED_UP, time(1))).isEqualTo(EventDecision.IGNORED_STALE);
        assertThat(shipment.applyCarrierEvent(CarrierEventType.RETURNED, time(3))).isEqualTo(EventDecision.CONFLICT);
        assertThat(shipment.status()).isEqualTo(ShippingModels.ShipmentStatus.DELIVERED);
    }

    @Test
    void newerFactThatDoesNotChangeStateIsNotReportedAsStale() {
        var shipment = registered();
        shipment.applyCarrierEvent(CarrierEventType.PICKED_UP, time(1));

        assertThat(shipment.applyCarrierEvent(CarrierEventType.IN_TRANSIT, time(2)))
                .isEqualTo(EventDecision.NO_TRANSITION);
        assertThat(shipment.lastCarrierEventAt()).isEqualTo(time(2));
        assertThat(shipment.applyCarrierEvent(CarrierEventType.DELIVERED, time(1)))
                .isEqualTo(EventDecision.IGNORED_STALE);
    }

    @Test
    void carrierEventCommandValidatesAndCanonicalizesBoundaryValues() {
        var command = new CarrierEventCommand("evt-1", "PRIMARY", "TRK-1", CarrierEventType.PICKED_UP,
                OffsetDateTime.parse("2026-09-24T09:01:00.123456789+09:00"));

        assertThat(command.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-09-24T00:01:00.123456Z"));
        assertThatThrownBy(() -> new CarrierEventCommand(" ", "PRIMARY", "TRK-1",
                CarrierEventType.PICKED_UP, time(1))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CarrierEventCommand("evt-1", "PRIMARY", "TRK-1", null, time(1)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CarrierEventCommand("evt-1", "PRIMARY", "TRK-1",
                CarrierEventType.PICKED_UP, null)).isInstanceOf(DomainException.class);
    }

    private static Shipment registered() {
        var shipment = shipment();
        shipment.requestRegistration();
        shipment.confirmRegistration("PRIMARY", "기본 택배사", "TRK-1");
        return shipment;
    }

    private static Shipment shipment() {
        return new Shipment("ord-1", "mem-1",
                new Address("addr-1", "집", "받는이", "010", "서울", "서울", "01234", true));
    }

    private static OffsetDateTime time(int minute) {
        return OffsetDateTime.parse("2026-09-24T00:0" + minute + ":00Z");
    }
}
