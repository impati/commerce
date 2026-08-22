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

    /** 기록된 순서를 유지한다. */
    List<Notification> findAll();

    /** 특정 회원의 알림만. 전체 목록은 다른 회원의 주문 내용을 노출한다. */
    List<Notification> findByMemberId(String memberId);

    /** 발송이 필요한 메일을 오래된 것부터 가져온다. 아웃박스를 비우는 쪽에서 쓴다. */
    List<Notification> findPendingMail(int limit);
}
