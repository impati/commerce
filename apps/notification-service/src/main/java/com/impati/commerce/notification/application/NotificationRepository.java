package com.impati.commerce.notification.application;

import com.impati.commerce.notification.domain.NotificationModels.Notification;

import java.util.List;

/**
 * 알림 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface NotificationRepository {
    void save(Notification notification);

    /** 기록된 순서를 유지한다. */
    List<Notification> findAll();
}
