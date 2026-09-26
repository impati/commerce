package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;
import com.impati.commerce.order.domain.OrderModels.Address;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 주문 서비스가 정본으로 소유하는 반품 사가 진행 상태. */
public final class ReturnProgress {
    public enum Status {
        REQUESTED, PICKUP_SCHEDULED, PICKUP_FAILED, RESCHEDULE_PENDING,
        WITHDRAWAL_PENDING, IN_TRANSIT, RECEIVED, COMPLETED, WITHDRAWN, ATTENTION_REQUIRED
    }
    public enum WorkStatus { NOT_READY, PENDING, SUCCEEDED }

    private final String id;
    private final String orderId;
    private final String memberId;
    private final String reason;
    private final String description;
    private final LocalDate awareDate;
    private final Money refundAmount;
    private Address pickupAddress;
    private Status status;
    private WorkStatus refundStatus;
    private WorkStatus inventoryStatus;
    private String returnShipmentId;
    private String disposition;
    private String condition;
    private final OffsetDateTime createdAt;
    private OffsetDateTime receivedAt;
    private OffsetDateTime inspectionDueAt;

    public ReturnProgress(String orderId, String memberId, String reason, String description, LocalDate awareDate,
            Money refundAmount, Address pickupAddress, OffsetDateTime createdAt) {
        this(Ids.newId("ret"), orderId, memberId, reason, description, awareDate, refundAmount, pickupAddress,
                Status.REQUESTED, WorkStatus.NOT_READY, WorkStatus.NOT_READY, null, null, null,
                createdAt, null, null);
    }

    public static ReturnProgress failedDelivery(String id, String orderId, String memberId, Money refundAmount,
            Address address, OffsetDateTime receivedAt) {
        return new ReturnProgress(id, orderId, memberId, "FAILED_DELIVERY", null, null, refundAmount, address,
                Status.RECEIVED, WorkStatus.PENDING, WorkStatus.NOT_READY, null, null, null,
                receivedAt, receivedAt, receivedAt.plusHours(72));
    }

    private ReturnProgress(String id, String orderId, String memberId, String reason, String description,
            LocalDate awareDate, Money refundAmount, Address pickupAddress, Status status,
            WorkStatus refundStatus, WorkStatus inventoryStatus, String returnShipmentId,
            String disposition, String condition, OffsetDateTime createdAt, OffsetDateTime receivedAt,
            OffsetDateTime inspectionDueAt) {
        this.id = id; this.orderId = orderId; this.memberId = memberId; this.reason = reason;
        this.description = description; this.awareDate = awareDate; this.refundAmount = refundAmount;
        this.pickupAddress = pickupAddress; this.status = status; this.refundStatus = refundStatus;
        this.inventoryStatus = inventoryStatus; this.returnShipmentId = returnShipmentId;
        this.disposition = disposition; this.condition = condition; this.createdAt = createdAt;
        this.receivedAt = receivedAt; this.inspectionDueAt = inspectionDueAt;
    }

    public static ReturnProgress restore(String id, String orderId, String memberId, String reason,
            String description, LocalDate awareDate, Money refundAmount, Address pickupAddress, String status,
            String refundStatus, String inventoryStatus, String returnShipmentId, String disposition,
            String condition, OffsetDateTime createdAt, OffsetDateTime receivedAt, OffsetDateTime inspectionDueAt) {
        return new ReturnProgress(id, orderId, memberId, reason, description, awareDate, refundAmount, pickupAddress,
                Status.valueOf(status), WorkStatus.valueOf(refundStatus), WorkStatus.valueOf(inventoryStatus),
                returnShipmentId, disposition, condition, createdAt, receivedAt, inspectionDueAt);
    }

    public void pickupScheduled(String shipmentId) {
        if (status == Status.WITHDRAWN || status == Status.IN_TRANSIT || status == Status.RECEIVED) return;
        returnShipmentId = shipmentId;
        status = Status.PICKUP_SCHEDULED;
    }
    public void pickedUp() {
        if (status == Status.IN_TRANSIT || status == Status.RECEIVED) return;
        if (status == Status.WITHDRAWN || status == Status.COMPLETED) throw DomainException.conflict("return cannot be picked up");
        status = Status.IN_TRANSIT;
        refundStatus = WorkStatus.PENDING;
    }
    public void pickupFailed() {
        if (status != Status.IN_TRANSIT && status != Status.RECEIVED) status = Status.PICKUP_FAILED;
    }
    public void received(OffsetDateTime at) {
        if (status == Status.RECEIVED || status == Status.COMPLETED) return;
        status = Status.RECEIVED;
        if (refundStatus == WorkStatus.NOT_READY) refundStatus = WorkStatus.PENDING;
        receivedAt = at;
        inspectionDueAt = at.plusHours(72);
    }
    public void requestWithdrawal() {
        if (status != Status.REQUESTED && status != Status.PICKUP_SCHEDULED && status != Status.PICKUP_FAILED) {
            throw DomainException.conflict("return can only be withdrawn before pickup");
        }
        status = Status.WITHDRAWAL_PENDING;
    }
    public void withdrawn() { status = Status.WITHDRAWN; }
    public void requestReschedule(Address address) {
        if (status != Status.PICKUP_FAILED) throw DomainException.conflict("only a failed pickup can be rescheduled");
        pickupAddress = address;
        status = Status.RESCHEDULE_PENDING;
    }
    public void inspect(String disposition, String condition) {
        if (status != Status.RECEIVED) throw DomainException.conflict("return has not been received");
        if (!"SALEABLE".equals(disposition) && !"NON_SALEABLE".equals(disposition)) {
            throw DomainException.validation("invalid inventory disposition");
        }
        if (condition == null || condition.isBlank()) {
            throw DomainException.validation("return condition is required");
        }
        if (inventoryStatus != WorkStatus.NOT_READY) {
            if (disposition.equals(this.disposition) && condition.equals(this.condition)) return;
            throw DomainException.conflict("return inventory disposition is already fixed");
        }
        this.disposition = disposition; this.condition = condition;
        this.inventoryStatus = WorkStatus.PENDING;
    }
    public void expireInspection() {
        if (status == Status.RECEIVED && inventoryStatus == WorkStatus.NOT_READY) {
            disposition = "NON_SALEABLE"; condition = "UNINSPECTED_TIMEOUT";
            inventoryStatus = WorkStatus.PENDING;
        }
    }
    public void refundSucceeded() { refundStatus = WorkStatus.SUCCEEDED; }
    public void inventorySucceeded() { inventoryStatus = WorkStatus.SUCCEEDED; }
    public boolean completeIfReady() {
        if (status == Status.RECEIVED && refundStatus == WorkStatus.SUCCEEDED
                && inventoryStatus == WorkStatus.SUCCEEDED) { status = Status.COMPLETED; return true; }
        return false;
    }
    public void attention() {
        if (status != Status.WITHDRAWN && status != Status.COMPLETED) status = Status.ATTENTION_REQUIRED;
    }

    public String id() { return id; } public String orderId() { return orderId; }
    public String memberId() { return memberId; } public String reason() { return reason; }
    public String description() { return description; } public LocalDate awareDate() { return awareDate; }
    public Money refundAmount() { return refundAmount; } public Address pickupAddress() { return pickupAddress; }
    public Status status() { return status; } public WorkStatus refundStatus() { return refundStatus; }
    public WorkStatus inventoryStatus() { return inventoryStatus; } public String returnShipmentId() { return returnShipmentId; }
    public String disposition() { return disposition; } public String condition() { return condition; }
    public OffsetDateTime createdAt() { return createdAt; } public OffsetDateTime receivedAt() { return receivedAt; }
    public OffsetDateTime inspectionDueAt() { return inspectionDueAt; }
}
