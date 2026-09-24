package com.impati.commerce.shipping.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.CarrierEventCommand;
import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PD-0025의 접수·집하·배송·반송 전이와 멱등성을 고정한다. */
@SpringBootTest
@RequiresDatabase
class ShippingExecutorTest {
    private final String runId = UUID.randomUUID().toString();

    @Autowired
    private ShippingUseCase shippingUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    /** [PD-0025-R2] 새 배송에는 아직 택배사와 운송장이 없다. */
    @Test
    void newShipmentIsReadyWithoutCarrierRegistration() {
        var shipment = create("ord_ready");

        assertThat(shipment.status()).isEqualTo("READY");
        assertThat(shipment.carrierCode()).isNull();
        assertThat(shipment.trackingNumber()).isNull();
    }

    /** [PD-0025-R3, R4] 포장 완료 뒤 운송장이 생겨도 아직 집하 전이다. */
    @Test
    void packingCompletionRegistersOneTrackingNumberAndAwaitsPickup() {
        var shipment = create("ord_packing");

        var first = shippingUseCase.completePacking(shipment.id());
        var second = shippingUseCase.completePacking(shipment.id());

        assertThat(first.status()).isEqualTo("AWAITING_PICKUP");
        assertThat(first.carrierCode()).isEqualTo("PRIMARY");
        assertThat(first.trackingNumber()).isNotBlank();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from shipment_events where shipment_id = ? "
                + "and type = 'SHIPMENT_REGISTERED'", Integer.class, shipment.id())).isEqualTo(1);
    }

    @Test
    void creatingTheSameOrderTwiceReturnsTheSameShipment() {
        assertThat(create("ord_idem_create")).isEqualTo(create("ord_idem_create"));
    }

    @Test
    void creatingTheSameOrderWithAnotherAddressIsRejected() {
        create("ord_changed_create");

        assertThatThrownBy(() -> shippingUseCase.create(
                "ord_changed_create-" + runId, "mem_demo",
                new ShipmentAddress("adr_2", "office", "받는이", "010", "다른 주소", "서울", "01234", false)))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("conflict"));
    }

    /** [PD-0025-R5] 운송장이 있어도 집하 전이면 취소할 수 있다. */
    @Test
    void awaitingPickupShipmentCanBeCancelledIdempotently() {
        var shipment = registered("ord_cancel");

        var first = shippingUseCase.cancel(shipment.id());
        var second = shippingUseCase.cancel(shipment.id());

        assertThat(first.status()).isEqualTo("CANCELLED");
        assertThat(second).isEqualTo(first);
    }

    /** [PD-0025-R6] 집하 뒤에는 고객 취소가 아니라 반품 절차가 필요하다. */
    @Test
    void pickedUpShipmentCannotBeCancelled() {
        var shipment = registered("ord_picked_cancel");
        event(shipment, "picked", "PICKED_UP", time(1));

        assertThatThrownBy(() -> shippingUseCase.cancel(shipment.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already left");
    }

    /** [PD-0025-R9] 같은 사건은 상태를 한 번만 바꾼다. */
    @Test
    void duplicateCarrierEventReturnsDuplicateWithoutAnotherTransition() {
        var shipment = registered("ord_duplicate_event");
        var command = command(shipment, "same-event", "PICKED_UP", time(1));

        assertThat(shippingUseCase.receive(command).result()).isEqualTo("APPLIED");
        assertThat(shippingUseCase.receive(command).result()).isEqualTo("DUPLICATE");
        assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("IN_TRANSIT");
        assertThat(jdbc.queryForObject("select count(*) from shipment_events where shipment_id = ? "
                + "and type = 'SHIPMENT_PICKED_UP'", Integer.class, shipment.id())).isEqualTo(1);
    }

    /** [PD-0025-R9] 늦은 과거 사건은 현재 상태를 후퇴시키지 않는다. */
    @Test
    void staleEventDoesNotRegressCurrentState() {
        var shipment = registered("ord_stale_event");
        event(shipment, "delivered", "DELIVERED", time(3));

        var stale = event(shipment, "late-pickup", "PICKED_UP", time(1));

        assertThat(stale.result()).isEqualTo("IGNORED_STALE");
        assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("DELIVERED");
    }

    /** [PD-0025-R10] 서로 다른 종결 결과는 자동으로 덮어쓰지 않는다. */
    @Test
    void contradictoryTerminalEventRequiresAttention() {
        var shipment = registered("ord_terminal_conflict");
        event(shipment, "delivered", "DELIVERED", time(1));

        var conflict = event(shipment, "returned", "RETURNED", time(2));

        assertThat(conflict.result()).isEqualTo("CONFLICT");
        assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("DELIVERED");
    }

    /** [PD-0025-R11, R12] 최종 배송 실패부터 반송 흐름을 드러낸다. */
    @Test
    void finalDeliveryFailureStartsReturnAndReturnedCompletesIt() {
        var shipment = registered("ord_return");

        assertThat(event(shipment, "failed", "DELIVERY_FAILED", time(1)).shipmentStatus())
                .isEqualTo("RETURNING");
        assertThat(event(shipment, "returned", "RETURNED", time(2)).shipmentStatus())
                .isEqualTo("RETURNED");
    }

    /** [PD-0025-R6, PD-0024-R3] 집하와 취소 중 저장소에서 먼저 확정된 전이만 성공한다. */
    @Test
    void pickupAndCancellationRaceHasOneWinner() throws Exception {
        var shipment = registered("ord_pickup_cancel_race");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = List.of(
                    executor.submit(() -> transition(start,
                            () -> event(shipment, "race-pickup", "PICKED_UP", time(1)))),
                    executor.submit(() -> transition(start, () -> shippingUseCase.cancel(shipment.id())))
            );
            start.countDown();

            var outcomes = results.stream().map(future -> {
                try {
                    return future.get(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            }).toList();

            assertThat(outcomes).contains("SUCCEEDED");
            assertThat(shippingUseCase.get(shipment.id()).status()).isIn("IN_TRANSIT", "CANCELLED");
        } finally {
            executor.shutdownNow();
        }
    }

    private ShipmentDetails create(String orderId) {
        return shippingUseCase.create(orderId + "-" + runId, "mem_demo", new ShipmentAddress(
                "adr_1", "home", "받는이", "010-0000-0000", "서울 어딘가 1", "서울", "01234", true));
    }

    private ShipmentDetails registered(String orderId) {
        return shippingUseCase.completePacking(create(orderId).id());
    }

    private com.impati.commerce.shipping.application.port.in.CarrierEventResult event(
            ShipmentDetails shipment, String eventId, String type, OffsetDateTime occurredAt
    ) {
        return shippingUseCase.receive(command(shipment, eventId, type, occurredAt));
    }

    private CarrierEventCommand command(
            ShipmentDetails shipment, String eventId, String type, OffsetDateTime occurredAt
    ) {
        return new CarrierEventCommand(shipment.id() + "-" + eventId,
                shipment.carrierCode(), shipment.trackingNumber(), type, occurredAt);
    }

    private static OffsetDateTime time(int minute) {
        return OffsetDateTime.parse("2026-09-24T00:0" + minute + ":00Z");
    }

    private static String transition(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
            return "SUCCEEDED";
        } catch (DomainException conflict) {
            return conflict.code().equals("conflict") ? "CONFLICT" : conflict.code();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
    }
}
