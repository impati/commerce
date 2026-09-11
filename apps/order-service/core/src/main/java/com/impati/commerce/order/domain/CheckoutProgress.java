package com.impati.commerce.order.domain;

import java.time.OffsetDateTime;

/** 외부 호출 사이에서 다시 시작할 수 있도록 저장하는 체크아웃 진행 상태 (ADR-0018). */
public final class CheckoutProgress {
    public enum Stage {
        ACCEPTED,
        CART_DETACHED,
        INVENTORY_RESERVED,
        PAYMENT_AUTHORIZED,
        SHIPMENT_CREATED,
        CAPTURE_PENDING,
        PAYMENT_CAPTURED,
        ORDER_CONFIRMED,
        INVENTORY_COMMITTED,
        COMPLETED,
        COMPENSATING,
        FAILED,
        ATTENTION_REQUIRED
    }

    public enum Outcome { PROCESSING, SUCCEEDED, FAILED }

    private final String orderId;
    private final String memberId;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final String paymentToken;
    private final long expectedCartVersion;
    private Stage stage;
    private Outcome outcome;
    private String failureCode;
    private String paymentCleanupStatus;
    private String lastError;
    private String reservationId;
    private String paymentId;
    private String shipmentId;
    private String trackingNumber;
    private Stage resumeStage;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime leaseUntil;
    private long leaseGeneration;

    public CheckoutProgress(
            String orderId,
            String memberId,
            String idempotencyKey,
            String requestFingerprint,
            String paymentToken,
            long expectedCartVersion
    ) {
        this(orderId, memberId, idempotencyKey, requestFingerprint, paymentToken, expectedCartVersion,
                Stage.ACCEPTED, Outcome.PROCESSING, null, "NONE", null,
                null, null, null, null, null, null, null, 0);
    }

    private CheckoutProgress(
            String orderId, String memberId, String idempotencyKey, String requestFingerprint,
            String paymentToken, long expectedCartVersion, Stage stage, Outcome outcome,
            String failureCode, String paymentCleanupStatus, String lastError,
            String reservationId, String paymentId, String shipmentId, String trackingNumber,
            Stage resumeStage, OffsetDateTime nextAttemptAt, OffsetDateTime leaseUntil, long leaseGeneration
    ) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.paymentToken = paymentToken;
        this.expectedCartVersion = expectedCartVersion;
        this.stage = stage;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.paymentCleanupStatus = paymentCleanupStatus;
        this.lastError = lastError;
        this.reservationId = reservationId;
        this.paymentId = paymentId;
        this.shipmentId = shipmentId;
        this.trackingNumber = trackingNumber;
        this.resumeStage = resumeStage;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseUntil = leaseUntil;
        this.leaseGeneration = leaseGeneration;
    }

    public static CheckoutProgress restore(
            String orderId, String memberId, String idempotencyKey, String requestFingerprint,
            String paymentToken, long expectedCartVersion, String stage, String outcome,
            String failureCode, String paymentCleanupStatus, String lastError,
            String reservationId, String paymentId, String shipmentId, String trackingNumber,
            OffsetDateTime nextAttemptAt, OffsetDateTime leaseUntil, long leaseGeneration
    ) {
        return new CheckoutProgress(orderId, memberId, idempotencyKey, requestFingerprint,
                paymentToken, expectedCartVersion, Stage.valueOf(stage), Outcome.valueOf(outcome),
                failureCode, paymentCleanupStatus, lastError, reservationId, paymentId, shipmentId,
                trackingNumber, null, nextAttemptAt, leaseUntil, leaseGeneration);
    }

    public static CheckoutProgress restore(
            String orderId, String memberId, String idempotencyKey, String requestFingerprint,
            String paymentToken, long expectedCartVersion, String stage, String outcome,
            String failureCode, String paymentCleanupStatus, String lastError,
            String reservationId, String paymentId, String shipmentId, String trackingNumber,
            String resumeStage, OffsetDateTime nextAttemptAt, OffsetDateTime leaseUntil, long leaseGeneration
    ) {
        return new CheckoutProgress(orderId, memberId, idempotencyKey, requestFingerprint,
                paymentToken, expectedCartVersion, Stage.valueOf(stage), Outcome.valueOf(outcome),
                failureCode, paymentCleanupStatus, lastError, reservationId, paymentId, shipmentId,
                trackingNumber, resumeStage == null ? null : Stage.valueOf(resumeStage),
                nextAttemptAt, leaseUntil, leaseGeneration);
    }

    public String orderId() { return orderId; }
    public String memberId() { return memberId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public String paymentToken() { return paymentToken; }
    public long expectedCartVersion() { return expectedCartVersion; }
    public Stage stage() { return stage; }
    public Outcome outcome() { return outcome; }
    public String failureCode() { return failureCode; }
    public String paymentCleanupStatus() { return paymentCleanupStatus; }
    public String lastError() { return lastError; }
    public String reservationId() { return reservationId; }
    public String paymentId() { return paymentId; }
    public String shipmentId() { return shipmentId; }
    public String trackingNumber() { return trackingNumber; }
    public Stage resumeStage() { return resumeStage; }
    public OffsetDateTime nextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime leaseUntil() { return leaseUntil; }
    public long leaseGeneration() { return leaseGeneration; }

    public void claimed(long generation, OffsetDateTime leaseUntil) {
        this.leaseGeneration = generation;
        this.leaseUntil = leaseUntil;
    }

    public void advance(Stage stage) {
        this.stage = stage;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void reservation(String reservationId) { this.reservationId = reservationId; }
    public void payment(String paymentId) { this.paymentId = paymentId; }
    public void shipment(String shipmentId, String trackingNumber) {
        this.shipmentId = shipmentId;
        this.trackingNumber = trackingNumber;
    }

    public void retryLater(String error, OffsetDateTime nextAttemptAt) {
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void compensate(String failureCode, String error, boolean paymentMayBeCaptured) {
        this.stage = Stage.COMPENSATING;
        this.outcome = Outcome.FAILED;
        this.failureCode = failureCode;
        this.lastError = truncate(error);
        this.paymentCleanupStatus = paymentMayBeCaptured ? "CHECKING" : "CANCELLING";
        this.nextAttemptAt = null;
    }

    public void paymentCleanup(String status) { this.paymentCleanupStatus = status; }

    public void fail() {
        this.stage = Stage.FAILED;
        this.outcome = Outcome.FAILED;
        this.paymentCleanupStatus = "DONE";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.resumeStage = null;
    }

    public void succeed() {
        this.stage = Stage.COMPLETED;
        this.outcome = Outcome.SUCCEEDED;
        this.paymentCleanupStatus = "NONE";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.resumeStage = null;
    }

    public void attention(String error) {
        this.resumeStage = this.stage;
        this.stage = Stage.ATTENTION_REQUIRED;
        this.lastError = truncate(error);
        this.nextAttemptAt = null;
        this.leaseUntil = null;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
