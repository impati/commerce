package com.impati.commerce.notification.application.port.in;

/**
 * 기록된 알림.
 *
 * <p>서비스 간 계약({@code ApiContracts})을 돌려주지 않는다. 유스케이스의 반환값은 서비스
 * 사이에서 오가는 것이 아니므로 인바운드 어댑터가 각자 자기 표현으로 옮긴다.
 */
public record NotificationDetails(String id, String eventType, String memberId, String subject, String body) {
}
