package com.impati.commerce.notification.adapter.in.scheduler;

import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아웃박스를 주기적으로 비운다.
 *
 * <p>스케줄러가 트리거인 진입점이므로 컨트롤러와 같은 등급이고 {@code adapter/in}에 산다.
 * 애플리케이션을 바깥에서 호출하는 것이지 애플리케이션의 일부가 아니다.
 *
 * <p>발송을 요청 처리에서 떼어내는 것이 목적이다. 인스턴스가 여러 개면 같은 항목을 두 번 집을 수
 * 있으므로 운영에서는 조회에 잠금을 걸거나 리더 선출이 필요하다. 지금은 단일 인스턴스를 전제한다.
 */
@Component
public class OutboxDispatcher {
    private final NotificationUseCase notificationUseCase;

    public OutboxDispatcher(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    @Scheduled(fixedDelayString = "${notifications.dispatch-interval:1000}")
    void dispatch() {
        notificationUseCase.dispatchPending();
    }
}
