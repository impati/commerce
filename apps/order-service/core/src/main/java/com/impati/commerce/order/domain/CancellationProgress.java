package com.impati.commerce.order.domain;

import java.time.OffsetDateTime;

/** 고객 주문 취소가 외부 서비스 사이에서 다시 시작할 수 있도록 저장하는 진행 상태 (ADR-0027). */
public final class CancellationProgress {

    public enum Stage {
        REQUESTED,
        SHIPMENT_CANCELLED,
        PAYMENT_REFUNDED,
        INVENTORY_RESTORED,
        COMPLETED,
        REJECTED,
        ATTENTION_REQUIRED
    }

    private final String orderId;
    private final String memberId;
    private Stage stage;
    private String failureCode;
    private String lastError;
    private Stage resumeStage;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime leaseUntil;
    private long leaseGeneration;

    public CancellationProgress(String orderId, String memberId) {
        this(orderId, memberId, Stage.REQUESTED, null, null, null, null, null, 0);
    }

    private CancellationProgress(
            String orderId,
            String memberId,
            Stage stage,
            String failureCode,
            String lastError,
            Stage resumeStage,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime leaseUntil,
            long leaseGeneration
    ) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.stage = stage;
        this.failureCode = failureCode;
        this.lastError = lastError;
        this.resumeStage = resumeStage;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseUntil = leaseUntil;
        this.leaseGeneration = leaseGeneration;
    }

    public static CancellationProgress restore(
            String orderId,
            String memberId,
            String stage,
            String failureCode,
            String lastError,
            String resumeStage,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime leaseUntil,
            long leaseGeneration
    ) {
        return new CancellationProgress(orderId, memberId, Stage.valueOf(stage), failureCode, lastError,
                resumeStage == null ? null : Stage.valueOf(resumeStage), nextAttemptAt, leaseUntil, leaseGeneration);
    }

    public String orderId() { return orderId; }
    public String memberId() { return memberId; }
    public Stage stage() { return stage; }
    public String failureCode() { return failureCode; }
    public String lastError() { return lastError; }
    public Stage resumeStage() { return resumeStage; }
    public OffsetDateTime nextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime leaseUntil() { return leaseUntil; }
    public long leaseGeneration() { return leaseGeneration; }

    public void claimed(long generation, OffsetDateTime until) {
        leaseGeneration = generation;
        leaseUntil = until;
    }

    public void advance(Stage next) {
        stage = next;
        nextAttemptAt = null;
        lastError = null;
    }

    public void retryLater(String error, OffsetDateTime retryAt) {
        lastError = truncate(error);
        nextAttemptAt = retryAt;
    }

    public void reject(String code, String error) {
        stage = Stage.REJECTED;
        failureCode = code;
        lastError = truncate(error);
        nextAttemptAt = null;
        leaseUntil = null;
        resumeStage = null;
    }

    public void complete() {
        stage = Stage.COMPLETED;
        failureCode = null;
        lastError = null;
        nextAttemptAt = null;
        leaseUntil = null;
        resumeStage = null;
    }

    public void attention(String error) {
        resumeStage = stage;
        stage = Stage.ATTENTION_REQUIRED;
        lastError = truncate(error);
        nextAttemptAt = null;
        leaseUntil = null;
    }

    public String customerStatus() {
        return switch (stage) {
            case COMPLETED -> "COMPLETED";
            case ATTENTION_REQUIRED -> "CHECKING";
            case REJECTED -> "NONE";
            default -> "PROCESSING";
        };
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
