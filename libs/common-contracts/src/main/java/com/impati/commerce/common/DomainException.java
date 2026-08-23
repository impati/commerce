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

    /**
     * 의존하는 것이 지금 응답하지 못한다. <b>요청의 문제가 아니라 우리 쪽의 문제</b>다.
     *
     * <p>{@link #outcomeUnknown}과 다르다. 저쪽은 부수효과가 일어났는지 모르는 상태이고
     * 여기는 아무 일도 일어나지 않은 것이 분명한 상태다 — 조회처럼 부수효과가 없거나,
     * 요청이 상대에게 닿지 못한 경우다. 그래서 그대로 다시 시도해도 안전하다.
     *
     * <p>요청의 문제로 옮기지 않는 것이 요점이다. 4xx로 답하면 호출자는 자기 요청을 고치려
     * 들고, 재시도하면 되는 상황에서 재시도하지 않는다.
     */
    public static DomainException unavailable(String message) {
        return new DomainException("service_unavailable", message, 503);
    }
}