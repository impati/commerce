package com.impati.commerce.member.application;

/**
 * notification-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다.
 *
 * <p>메일 발송 자체는 notification-service가 소유한다. member-service는 "인증 메일을 보내달라"고
 * 요청만 하고 메일 벤더를 알지 못한다.
 */
public interface NotificationClient {
    void requestEmailVerification(String memberId, String email, String token);
}
