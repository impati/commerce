package com.impati.commerce.member.application.port.out;

/**
 * notification-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다.
 *
 * <p>메일 발송 자체는 notification-service가 소유한다. member-service는 "인증 메일을 보내달라"고
 * 요청만 하고 메일 벤더를 알지 못한다.
 */
public interface NotificationClient {
    /**
     * 인증 메일 발송을 요청한다.
     *
     * <p>{@code idempotencyKey}로 아웃박스 행 id를 넘긴다. 재시도는 같은 키로 가므로
     * notification-service가 중복을 걸러낸다 (ADR-0011).
     */
    void requestEmailVerification(String memberId, String email, String token, String idempotencyKey);
}
