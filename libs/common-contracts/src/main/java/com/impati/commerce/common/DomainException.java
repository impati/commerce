package com.impati.commerce.common;

public class DomainException extends RuntimeException {
    private final String code;
    private final int status;

    public DomainException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public static DomainException validation(String message) {
        return new DomainException("validation_error", message, 400);
    }

    public static DomainException notFound(String message) {
        return new DomainException("not_found", message, 404);
    }

    public static DomainException conflict(String message) {
        return new DomainException("conflict", message, 409);
    }

    public static DomainException paymentDeclined(String message) {
        return new DomainException("payment_declined", message, 402);
    }
}

