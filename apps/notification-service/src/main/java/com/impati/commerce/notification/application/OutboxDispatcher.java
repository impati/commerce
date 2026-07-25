package com.impati.commerce.notification.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아웃박스를 주기적으로 비운다.
 *
 * <p>발송을 요청 처리에서 떼어내는 것이 목적이다. 인스턴스가 여러 개면 같은 항목을 두 번 집을 수
 * 있으므로 운영에서는 조회에 잠금을 걸거나 리더 선출이 필요하다. 지금은 단일 인스턴스를 전제한다.
 */
@Component
public class OutboxDispatcher {
    private final NotificationService notifications;

    public OutboxDispatcher(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${notifications.dispatch-interval:1000}")
    void dispatch() {
        notifications.dispatchPending();
    }
}
