package com.impati.commerce.inventory.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.util.ArrayList;
import java.util.List;

public final class InventoryModels {
    private InventoryModels() {
    }

    /** 예약 한 줄. 계약의 {@code ReservationLine}과 모양이 같지만 도메인 타입이다. */
    public record ReservedLine(String skuId, int quantity) {
        public ReservedLine {
            if (quantity <= 0) {
                throw DomainException.validation("reservation quantity must be positive");
            }
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

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
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

        public void add(int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("stock quantity must be positive");
            }
            onHand += quantity;
        }

        public void reserve(int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("reservation quantity must be positive");
            }
            if (available() < quantity) {
                throw DomainException.conflict("insufficient stock for " + skuId);
            }
            reserved += quantity;
        }

        public void commit(int quantity) {
            if (reserved < quantity) {
                throw DomainException.conflict("reservation mismatch for " + skuId);
            }
            reserved -= quantity;
            onHand -= quantity;
        }

        public void release(int quantity) {
            if (reserved < quantity) {
                throw DomainException.conflict("reservation mismatch for " + skuId);
            }
            reserved -= quantity;
        }
    }

    public static final class Reservation {
        private final String id;
        private final String orderId;
        private final List<ReservedLine> lines;
        private String status;

        public Reservation(String orderId, List<ReservedLine> lines) {
            this(Ids.newId("rsv"), orderId, lines, "RESERVED");
        }

        private Reservation(String id, String orderId, List<ReservedLine> lines, String status) {
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
        public static Reservation restore(String id, String orderId, List<ReservedLine> lines, String status) {
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

        public String status() {
            return status;
        }

        public void commit() {
            ensureReserved();
            status = "COMMITTED";
        }

        public void release() {
            ensureReserved();
            status = "RELEASED";
        }

        private void ensureReserved() {
            if (!status.equals("RESERVED")) {
                throw DomainException.conflict("reservation is not reserved");
            }
        }
    }
}
