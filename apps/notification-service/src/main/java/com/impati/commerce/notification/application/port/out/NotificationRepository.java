package com.impati.commerce.notification.application.port.out;

import com.impati.commerce.notification.domain.NotificationModels.Notification;

import java.util.List;

/**
 * 알림 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>조회로 얻은 객체를 변형한 것만으로 저장됐다고 가정하지 말 것. 변경했으면 {@link #save}를
 * 명시적으로 부른다.
 */
public interface NotificationRepository {
    void save(Notification notification);

    /**
     * 같은 멱등 키의 알림이 없을 때만 기록하고, 저장된 것을 돌려준다 (ADR-0011).
     *
     * <p>이미 있으면 새로 만들지 않고 <b>먼저 기록된 것</b>을 돌려준다. 그것이 멱등 수신의
     * 정의다 — 두 번째 요청은 첫 번째의 결과를 본다.
     *
     * <p>승자를 정하는 것은 조회가 아니라 유니크 제약이다. 조회로 먼저 확인하고 없으면 넣는
     * 방식은 동시에 들어온 두 요청이 둘 다 "없음"을 읽는 경합을 남긴다.
     */
    Notification saveIfAbsent(Notification notification);

    /** 기록된 순서를 유지한다. */
    List<Notification> findAll();

    /** 특정 회원의 알림만. 전체 목록은 다른 회원의 주문 내용을 노출한다. */
    List<Notification> findByMemberId(String memberId);

    /** 발송이 필요한 메일을 오래된 것부터 가져온다. 아웃박스를 비우는 쪽에서 쓴다. */
    List<Notification> findPendingMail(int limit);
}
