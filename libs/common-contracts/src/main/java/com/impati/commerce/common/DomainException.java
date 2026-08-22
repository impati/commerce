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

    /**
     * 협력자에게 요청은 보냈으나 결과를 받지 못했다. 실패가 아니라 <b>모름</b>이다.
     *
     * <p>전송이 끊기거나 시간이 초과된 경우이며, 상대는 정상적으로 처리를 마쳤을 수 있다.
     * 이것을 실패로 단정하면 이미 일어난 일을 되돌리게 된다.
     */
    public static DomainException outcomeUnknown(String message) {
        return new DomainException("outcome_unknown", message, 502);
    }
}