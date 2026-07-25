package com.impati.commerce.inventory.domain;

import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.util.ArrayList;
import java.util.List;

public final class InventoryModels {
    private InventoryModels() {
    }

    public static final class StockItem {
        private final String skuId;
        private int onHand;
        private int reserved;

        public StockItem(String skuId) {
            this.skuId = skuId;
        }

        public String skuId() {
            return skuId;
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

        public StockResponse toResponse() {
            return new StockResponse(skuId, onHand, reserved, available());
        }
    }

    public static final class Reservation {
        private final String id;
        private final String orderId;
        private final List<ReservationLine> lines;
        private String status = "RESERVED";

        public Reservation(String orderId, List<ReservationLine> lines) {
            if (lines.isEmpty()) {
                throw DomainException.validation("reservation requires at least one line");
            }
            this.id = Ids.newId("rsv");
            this.orderId = orderId;
            this.lines = new ArrayList<>(lines);
        }

        public String id() {
            return id;
        }

        public String orderId() {
            return orderId;
        }

        public List<ReservationLine> lines() {
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

        public ReservationResponse toResponse() {
            return new ReservationResponse(id, orderId, status, lines());
        }

        private void ensureReserved() {
            if (!status.equals("RESERVED")) {
                throw DomainException.conflict("reservation is not reserved");
            }
        }
    }
}

