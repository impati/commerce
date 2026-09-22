package com.impati.commerce.inventory.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InventoryModels {

    public enum ReservationStatus {
        RESERVED,
        COMMITTED,
        RELEASED,
        RESTORED
    }

    public enum TransitionOutcome {
        APPLIED,
        UNCHANGED
    }

    public enum MovementReason {
        STOCK_INCREASED,
        RESERVATION_CREATED,
        RESERVATION_COMMITTED,
        RESERVATION_RELEASED,
        RESERVATION_RESTORED
    }

    private InventoryModels() {
    }

    /**
     * 예약 한 줄. 계약의 {@code ReservationLine}과 모양이 같지만 도메인 타입이다.
     */
    public record ReservedLine(String skuId, int quantity) {

        public ReservedLine {
            if (quantity <= 0) {
                throw DomainException.validation("reservation quantity must be positive");
            }
        }
    }

    /**
     * 한 재고 이동이 SKU 하나에 만든 변화와 그 결과다.
     *
     * <p>반영 전 잔액은 {@code after - delta}로 구한다. 증감과 결과를 함께 고정해 현재 재고가
     * 어느 이동에서 어긋났는지 확인할 수 있게 한다 (ADR-0028).
     */
    public record MovementLine(
            String skuId,
            int onHandDelta,
            int reservedDelta,
            int onHandAfter,
            int reservedAfter
    ) {

        public MovementLine {
            Objects.requireNonNull(skuId);
            if (onHandDelta == 0 && reservedDelta == 0) {
                throw DomainException.validation("inventory movement must change stock");
            }
            if (onHandAfter < 0 || reservedAfter < 0 || onHandAfter < reservedAfter) {
                throw DomainException.validation("inventory movement has invalid resulting stock");
            }
            var onHandBefore = onHandAfter - onHandDelta;
            var reservedBefore = reservedAfter - reservedDelta;
            if (onHandBefore < 0 || reservedBefore < 0 || onHandBefore < reservedBefore) {
                throw DomainException.validation("inventory movement has invalid previous stock");
            }
        }
    }

    /** 실제로 적용된 한 번의 재고 사건. 요청 시도 로그와 달리 품목 줄은 항상 수량을 바꾼다. */
    public static final class InventoryMovement {

        private final String id;
        private final MovementReason reason;
        private final String orderId;
        private final String reservationId;
        private final LocalDateTime occurredAt;
        private final List<MovementLine> lines;

        private InventoryMovement(
                MovementReason reason,
                String orderId,
                String reservationId,
                LocalDateTime occurredAt,
                List<MovementLine> lines
        ) {
            if (lines.isEmpty()) {
                throw DomainException.validation("inventory movement requires at least one line");
            }
            this.id = Ids.newId("mov");
            this.reason = Objects.requireNonNull(reason);
            this.orderId = orderId;
            this.reservationId = reservationId;
            this.occurredAt = Objects.requireNonNull(occurredAt).truncatedTo(ChronoUnit.MICROS);
            this.lines = List.copyOf(lines);
        }

        public static InventoryMovement stockIncreased(LocalDateTime occurredAt, MovementLine line) {
            return new InventoryMovement(
                    MovementReason.STOCK_INCREASED,
                    null,
                    null,
                    occurredAt,
                    List.of(line)
            );
        }

        public static InventoryMovement forReservation(
                MovementReason reason,
                Reservation reservation,
                LocalDateTime occurredAt,
                List<MovementLine> lines
        ) {
            if (reason == MovementReason.STOCK_INCREASED) {
                throw DomainException.validation("stock increase is not a reservation movement");
            }
            Objects.requireNonNull(reservation);
            return new InventoryMovement(
                    reason,
                    reservation.orderId(),
                    reservation.id(),
                    occurredAt,
                    lines
            );
        }

        public String id() {
            return id;
        }

        public MovementReason reason() {
            return reason;
        }

        public String orderId() {
            return orderId;
        }

        public String reservationId() {
            return reservationId;
        }

        public LocalDateTime occurredAt() {
            return occurredAt;
        }

        public List<MovementLine> lines() {
            return lines;
        }
    }

    public static final class StockItem {

        private final String skuId;
        private int onHand;
        private int reserved;

        public StockItem(String skuId) {
            this(skuId, 0, 0);
        }

        private StockItem(String skuId, int onHand, int reserved) {
            this.skuId = skuId;
            this.onHand = onHand;
            this.reserved = reserved;
        }

        /**
         * 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다.
         */
        public static StockItem restore(String skuId, int onHand, int reserved) {
            return new StockItem(skuId, onHand, reserved);
        }

        public String skuId() {
            return skuId;
        }

        public int onHand() {
            return onHand;
        }

        public int reserved() {
            return reserved;
        }

        public int available() {
            return onHand - reserved;
        }

        public MovementLine add(int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("stock quantity must be positive");
            }
            var onHandBefore = onHand;
            var reservedBefore = reserved;
            onHand += quantity;
            return changedFrom(onHandBefore, reservedBefore);
        }

        public MovementLine reserve(int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("reservation quantity must be positive");
            }
            if (available() < quantity) {
                throw DomainException.conflict("insufficient stock for " + skuId);
            }
            var onHandBefore = onHand;
            var reservedBefore = reserved;
            reserved += quantity;
            return changedFrom(onHandBefore, reservedBefore);
        }

        public MovementLine commit(int quantity) {
            if (reserved < quantity) {
                throw DomainException.conflict("reservation mismatch for " + skuId);
            }
            var onHandBefore = onHand;
            var reservedBefore = reserved;
            reserved -= quantity;
            onHand -= quantity;
            return changedFrom(onHandBefore, reservedBefore);
        }

        public MovementLine release(int quantity) {
            if (reserved < quantity) {
                throw DomainException.conflict("reservation mismatch for " + skuId);
            }
            var onHandBefore = onHand;
            var reservedBefore = reserved;
            reserved -= quantity;
            return changedFrom(onHandBefore, reservedBefore);
        }

        /**
         * [PD-0024-R6] 확정된 판매를 취소해 보유 수량으로 되돌린다.
         */
        public MovementLine restore(int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("restored stock quantity must be positive");
            }
            var onHandBefore = onHand;
            var reservedBefore = reserved;
            onHand += quantity;
            return changedFrom(onHandBefore, reservedBefore);
        }

        private MovementLine changedFrom(int onHandBefore, int reservedBefore) {
            return new MovementLine(
                    skuId,
                    onHand - onHandBefore,
                    reserved - reservedBefore,
                    onHand,
                    reserved
            );
        }
    }

    public static final class Reservation {

        private final String id;
        private final String orderId;
        private final List<ReservedLine> lines;
        private ReservationStatus status;

        public Reservation(String orderId, List<ReservedLine> lines) {
            this(Ids.newId("rsv"), orderId, lines, ReservationStatus.RESERVED);
        }

        private Reservation(String id, String orderId, List<ReservedLine> lines, ReservationStatus status) {
            if (lines.isEmpty()) {
                throw DomainException.validation("reservation requires at least one line");
            }
            this.id = id;
            this.orderId = orderId;
            this.lines = new ArrayList<>(lines);
            this.status = status;
        }

        /**
         * 저장된 상태에서 복원한다.
         *
         * <p>상태 전이 규칙을 거치지 않고 status를 그대로 세운다. 영속화 어댑터만 쓴다.
         */
        public static Reservation restore(
                String id,
                String orderId,
                List<ReservedLine> lines,
                ReservationStatus status
        ) {
            return new Reservation(id, orderId, lines, status);
        }

        public String id() {
            return id;
        }

        public String orderId() {
            return orderId;
        }

        public List<ReservedLine> lines() {
            return List.copyOf(lines);
        }

        public ReservationStatus status() {
            return status;
        }

        public TransitionOutcome commit() {
            if (status == ReservationStatus.COMMITTED) {
                return TransitionOutcome.UNCHANGED;
            }
            ensureReserved();
            status = ReservationStatus.COMMITTED;
            return TransitionOutcome.APPLIED;
        }

        public TransitionOutcome release() {
            if (status == ReservationStatus.RELEASED) {
                return TransitionOutcome.UNCHANGED;
            }
            ensureReserved();
            status = ReservationStatus.RELEASED;
            return TransitionOutcome.APPLIED;
        }

        /**
         * [PD-0024-R6][PD-0024-R7] 확정된 예약만 한 번 복원한다.
         */
        public TransitionOutcome restore() {
            if (status == ReservationStatus.RESTORED) {
                return TransitionOutcome.UNCHANGED;
            }
            if (status != ReservationStatus.COMMITTED) {
                throw DomainException.conflict("only committed reservation can be restored");
            }
            status = ReservationStatus.RESTORED;
            return TransitionOutcome.APPLIED;
        }

        private void ensureReserved() {
            if (status != ReservationStatus.RESERVED) {
                throw DomainException.conflict("reservation is not reserved");
            }
        }
    }
}
