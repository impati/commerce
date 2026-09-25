package com.impati.commerce.shipping.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.CarrierEventCommand;
import com.impati.commerce.shipping.application.port.in.CarrierOperationRecoveryUseCase;
import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import com.impati.commerce.shipping.domain.ShippingModels.CarrierEventType;
import com.impati.commerce.shipping.application.port.out.CarrierGateway;
import com.impati.commerce.test.RequiresDatabase;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(ShippingExecutorTest.CarrierTestConfiguration.class)
class ShippingExecutorTest {
    private final String runId = UUID.randomUUID().toString();

    @Autowired
    private ShippingUseCase shippingUseCase;

    @Autowired
    private CarrierOperationRecoveryUseCase recovery;

    @Autowired
    private RecordingCarrierGateway carrier;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetCarrier() {
        carrier.reset();
    }

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
        assertThat(carrier.registrationCalls.get()).isEqualTo(1);
    }

    @Test
    void registrationResponseLossIsRecoveredByQueryWithoutASecondMutation() {
        var shipment = create("ord_reg_loss");
        carrier.loseNextRegistrationResponse.set(true);

        assertThat(shippingUseCase.completePacking(shipment.id()).status()).isEqualTo("READY");
        makeOperationsDue();
        recovery.recoverPendingOperations(20);

        assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("AWAITING_PICKUP");
        assertThat(carrier.registrationCalls.get()).isEqualTo(1);
        assertThat(carrier.registrationQueries.get()).isEqualTo(1);
    }

    @Test
    void concurrentPackingCompletionCreatesOneCarrierRegistration() throws Exception {
        var shipment = create("ord_concurrent_packing");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var calls = List.of(
                    executor.submit(() -> { start.await(); return shippingUseCase.completePacking(shipment.id()); }),
                    executor.submit(() -> { start.await(); return shippingUseCase.completePacking(shipment.id()); })
            );
            start.countDown();

            calls.get(0).get(10, TimeUnit.SECONDS);
            calls.get(1).get(10, TimeUnit.SECONDS);
            assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("AWAITING_PICKUP");
            assertThat(carrier.registrationCalls.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from shipment_events where shipment_id = ? "
                    + "and type = 'SHIPMENT_REGISTERED'", Integer.class, shipment.id())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
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

    @Test
    void cancellationResponseLossIsRecoveredByQueryWithoutASecondMutation() {
        var shipment = registered("ord_cancel_loss");
        carrier.loseNextCancellationResponse.set(true);

        assertThat(shippingUseCase.cancel(shipment.id()).status()).isEqualTo("AWAITING_PICKUP");
        makeOperationsDue();
        recovery.recoverPendingOperations(20);

        assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("CANCELLED");
        assertThat(carrier.cancellationCalls.get()).isEqualTo(1);
        assertThat(carrier.cancellationQueries.get()).isEqualTo(1);
    }

    @Test
    void packingAndCancellationRaceConvergesWithoutAnOrphanRegistration() throws Exception {
        var shipment = create("ord_packing_cancel_race");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var packing = executor.submit(() -> transition(start,
                    () -> shippingUseCase.completePacking(shipment.id())));
            var cancellation = executor.submit(() -> transition(start,
                    () -> shippingUseCase.cancel(shipment.id())));
            start.countDown();
            packing.get(10, TimeUnit.SECONDS);
            cancellation.get(10, TimeUnit.SECONDS);
            makeOperationsDue();
            recovery.recoverPendingOperations(20);

            assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("CANCELLED");
            if (carrier.registrationCalls.get() > 0) {
                assertThat(carrier.cancellationCalls.get()).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
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

    @Test
    void sameEventInstantWithAnotherOffsetAndExcessNanosIsDuplicate() {
        var shipment = registered("ord_normalized_duplicate");
        var first = new CarrierEventCommand(shipment.id() + "-normalized", shipment.carrierCode(),
                shipment.trackingNumber(), CarrierEventType.PICKED_UP,
                OffsetDateTime.parse("2026-09-24T00:01:00.123456Z"));
        var replay = new CarrierEventCommand(shipment.id() + "-normalized", shipment.carrierCode(),
                shipment.trackingNumber(), CarrierEventType.PICKED_UP,
                OffsetDateTime.parse("2026-09-24T09:01:00.123456999+09:00"));

        assertThat(shippingUseCase.receive(first).result()).isEqualTo("APPLIED");
        assertThat(shippingUseCase.receive(replay).result()).isEqualTo("DUPLICATE");
    }

    @Test
    void sameEventIdWithDifferentCanonicalContentIsConflict() {
        var shipment = registered("ord_event_fingerprint");
        var first = command(shipment, "reused", "PICKED_UP", time(1));
        var changed = command(shipment, "reused", "DELIVERED", time(1));

        assertThat(shippingUseCase.receive(first).result()).isEqualTo("APPLIED");
        assertThat(shippingUseCase.receive(changed).result()).isEqualTo("CONFLICT");
        assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("IN_TRANSIT");
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

    @Test
    void sameStateEventAdvancesOrderingWatermarkWithoutPublishingAnotherCustomerEvent() {
        var shipment = registered("ord_same_state_watermark");
        event(shipment, "picked", "PICKED_UP", time(1));

        assertThat(event(shipment, "still-moving", "IN_TRANSIT", time(3)).result())
                .isEqualTo("NO_TRANSITION");
        assertThat(event(shipment, "older-delivery", "DELIVERED", time(2)).result())
                .isEqualTo("IGNORED_STALE");
        assertThat(shippingUseCase.get(shipment.id()).status()).isEqualTo("IN_TRANSIT");
        assertThat(jdbc.queryForObject("select count(*) from shipment_events where shipment_id = ? "
                + "and type = 'SHIPMENT_IN_TRANSIT'", Integer.class, shipment.id())).isEqualTo(0);
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

    @Test
    void returnPickupIsSeparateFromOutboundAndIdempotentByReturnId() {
        var outbound = create("ord_customer_return");
        var returnId = "ret_customer-" + runId;
        var address = new ShipmentAddress(
                "adr_return", "home", "반품인", "010-1111-2222", "서울 반품로 1", "서울", "01234", true);

        var first = shippingUseCase.createReturnShipment(
                returnId, outbound.orderId(), outbound.memberId(), address);
        var second = shippingUseCase.createReturnShipment(
                returnId, outbound.orderId(), outbound.memberId(), address);

        assertThat(first).isEqualTo(second);
        assertThat(first.id()).isNotEqualTo(outbound.id());
        assertThat(first.kind()).isEqualTo("RETURN");
        assertThat(first.returnId()).isEqualTo(returnId);
        assertThat(first.status()).isEqualTo("AWAITING_PICKUP");
        assertThat(carrier.pickupCalls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from shipments where order_id = ?", Integer.class,
                outbound.orderId())).isEqualTo(2);
    }

    @Test
    void pickedUpReturnIsDeliveredToSellerAndCannotBeWithdrawn() {
        var shipment = returnShipment("ret_picked");

        assertThat(event(shipment, "picked", "PICKED_UP", time(1)).shipmentStatus())
                .isEqualTo("IN_TRANSIT");
        assertThatThrownBy(() -> shippingUseCase.withdrawReturn(shipment.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already left");
        assertThat(event(shipment, "received", "DELIVERED", time(2)).shipmentStatus())
                .isEqualTo("DELIVERED");
    }

    @Test
    void failedReturnPickupCanBeRescheduledWithAnotherAddress() {
        var shipment = returnShipment("ret_reschedule");
        assertThat(event(shipment, "failed", "DELIVERY_FAILED", time(1)).shipmentStatus())
                .isEqualTo("PICKUP_FAILED");
        var changed = new ShipmentAddress(
                "adr_changed", "office", "반품인", "010-3333-4444", "서울 새주소 2", "서울", "04321", false);

        var rescheduled = shippingUseCase.rescheduleReturnPickup(shipment.id(), changed);

        assertThat(rescheduled.status()).isEqualTo("AWAITING_PICKUP");
        assertThat(rescheduled.address()).isEqualTo(changed);
        assertThat(carrier.pickupCalls.get()).isEqualTo(2);
        assertThat(rescheduled.trackingNumber()).isNotEqualTo(shipment.trackingNumber());
    }

    @Test
    void returnPickupCanBeWithdrawnBeforeCarrierPickup() {
        var shipment = returnShipment("ret_withdraw");

        var withdrawn = shippingUseCase.withdrawReturn(shipment.id());

        assertThat(withdrawn.status()).isEqualTo("CANCELLED");
        assertThat(carrier.pickupCancellationCalls.get()).isEqualTo(1);
    }

    @Test
    void withdrawnReturnDoesNotBlockANewReturnForTheSameOrder() {
        var orderId = "ord_reopened_return-" + runId;
        var address = new ShipmentAddress(
                "adr_return", "home", "반품인", "010-1111-2222", "서울 반품로 1", "서울", "01234", true);
        var first = shippingUseCase.createReturnShipment(
                "ret_first-" + runId, orderId, "mem_demo", address);
        shippingUseCase.withdrawReturn(first.id());

        var second = shippingUseCase.createReturnShipment(
                "ret_second-" + runId, orderId, "mem_demo", address);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo("AWAITING_PICKUP");
        assertThat(jdbc.queryForObject("select count(*) from shipments where order_id = ?", Integer.class, orderId))
                .isEqualTo(2);
    }

    private ShipmentDetails create(String orderId) {
        return shippingUseCase.create(orderId + "-" + runId, "mem_demo", new ShipmentAddress(
                "adr_1", "home", "받는이", "010-0000-0000", "서울 어딘가 1", "서울", "01234", true));
    }

    private ShipmentDetails registered(String orderId) {
        return shippingUseCase.completePacking(create(orderId).id());
    }

    private ShipmentDetails returnShipment(String returnId) {
        return shippingUseCase.createReturnShipment(
                returnId + "-" + runId,
                "ord_for_" + returnId + "-" + runId,
                "mem_demo",
                new ShipmentAddress("adr_return", "home", "반품인", "010-0000-0000",
                        "서울 반품로 1", "서울", "01234", true));
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
                shipment.carrierCode(), shipment.trackingNumber(), CarrierEventType.valueOf(type), occurredAt);
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

    private void makeOperationsDue() {
        jdbc.update("update carrier_operations set next_attempt_at = date_sub(utc_timestamp(6), interval 1 second), "
                + "claim_until = null where operation_status = 'PENDING'");
    }

    @TestConfiguration
    static class CarrierTestConfiguration {
        @Bean
        @Primary
        RecordingCarrierGateway recordingCarrierGateway() {
            return new RecordingCarrierGateway();
        }
    }

    static final class RecordingCarrierGateway implements CarrierGateway {
        private final ConcurrentHashMap<String, CarrierRegistration> registrations = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CarrierCancellation> cancellations = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CarrierRegistration> pickups = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CarrierCancellation> pickupCancellations = new ConcurrentHashMap<>();
        private final AtomicInteger registrationCalls = new AtomicInteger();
        private final AtomicInteger registrationQueries = new AtomicInteger();
        private final AtomicInteger cancellationCalls = new AtomicInteger();
        private final AtomicInteger cancellationQueries = new AtomicInteger();
        private final AtomicInteger pickupCalls = new AtomicInteger();
        private final AtomicInteger pickupQueries = new AtomicInteger();
        private final AtomicInteger pickupCancellationCalls = new AtomicInteger();
        private final AtomicInteger pickupCancellationQueries = new AtomicInteger();
        private final AtomicBoolean loseNextRegistrationResponse = new AtomicBoolean();
        private final AtomicBoolean loseNextCancellationResponse = new AtomicBoolean();

        @Override
        public CarrierRegistration register(RegistrationCommand command) {
            registrationCalls.incrementAndGet();
            var result = registrations.computeIfAbsent(command.idempotencyKey(), ignored ->
                    CarrierRegistration.confirmed("PRIMARY", "기본 택배사", "TRK-" + command.shipmentId()));
            if (loseNextRegistrationResponse.compareAndSet(true, false)) {
                throw DomainException.outcomeUnknown("registration response was lost");
            }
            return result;
        }

        @Override
        public CarrierRegistration registration(RegistrationCommand command) {
            registrationQueries.incrementAndGet();
            return registrations.getOrDefault(command.idempotencyKey(), CarrierRegistration.absent());
        }

        @Override
        public CarrierCancellation cancel(CancellationCommand command) {
            cancellationCalls.incrementAndGet();
            var result = cancellations.computeIfAbsent(command.idempotencyKey(), ignored -> CarrierCancellation.confirmed());
            if (loseNextCancellationResponse.compareAndSet(true, false)) {
                throw DomainException.outcomeUnknown("cancellation response was lost");
            }
            return result;
        }

        @Override
        public CarrierCancellation cancellation(CancellationCommand command) {
            cancellationQueries.incrementAndGet();
            return cancellations.getOrDefault(command.idempotencyKey(), CarrierCancellation.absent());
        }

        @Override
        public CarrierRegistration schedulePickup(PickupCommand command) {
            pickupCalls.incrementAndGet();
            return pickups.computeIfAbsent(command.idempotencyKey(), ignored -> CarrierRegistration.confirmed(
                    "PRIMARY", "기본 택배사", "RTN-" + command.idempotencyKey()));
        }

        @Override
        public CarrierRegistration pickup(PickupCommand command) {
            pickupQueries.incrementAndGet();
            return pickups.getOrDefault(command.idempotencyKey(), CarrierRegistration.absent());
        }

        @Override
        public CarrierCancellation cancelPickup(CancellationCommand command) {
            pickupCancellationCalls.incrementAndGet();
            return pickupCancellations.computeIfAbsent(
                    command.idempotencyKey(), ignored -> CarrierCancellation.confirmed());
        }

        @Override
        public CarrierCancellation pickupCancellation(CancellationCommand command) {
            pickupCancellationQueries.incrementAndGet();
            return pickupCancellations.getOrDefault(command.idempotencyKey(), CarrierCancellation.absent());
        }

        void reset() {
            registrations.clear();
            cancellations.clear();
            pickups.clear();
            pickupCancellations.clear();
            registrationCalls.set(0);
            registrationQueries.set(0);
            cancellationCalls.set(0);
            cancellationQueries.set(0);
            pickupCalls.set(0);
            pickupQueries.set(0);
            pickupCancellationCalls.set(0);
            pickupCancellationQueries.set(0);
            loseNextRegistrationResponse.set(false);
            loseNextCancellationResponse.set(false);
        }
    }
}
