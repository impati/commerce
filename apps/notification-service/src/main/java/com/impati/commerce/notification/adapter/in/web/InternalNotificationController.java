package com.impati.commerce.notification.adapter.in.web;

import com.impati.commerce.common.ApiContracts.EmailVerificationMailRequest;
import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.ApiContracts.OutboxEntryResponse;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 게이트웨이가 노출하지 않는 경로. 형제 서비스와 운영이 부른다 (ADR-0003).
 *
 * <p>알림 기록은 order-service가, 인증 메일 요청은 member-service가 부른다. 둘 다 임의의
 * {@code memberId} 앞으로 알림을 만들 수 있으므로 사용자에게 열 수 없다.
 */
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {
    private final NotificationUseCase notifications;

    public InternalNotificationController(NotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/events")
    NotificationResponse record(@RequestBody NotificationEventRequest request) {
        return NotificationResponseMapper.from(notifications.record(
                request.eventType(), request.memberId(), request.subject(), request.body()));
    }

    @PostMapping("/email-verifications")
    NotificationResponse requestEmailVerification(@RequestBody EmailVerificationMailRequest request) {
        return NotificationResponseMapper.from(notifications.requestEmailVerification(
                request.memberId(), request.email(), request.token()));
    }

    /**
     * 발송함 조회. {@code local} 프로파일에서만 존재한다.
     *
     * <p>메일 본문에는 인증 토큰이 들어 있다. 이 경로가 열려 있으면 남의 계정을 인증해 가로챌 수
     * 있다. 내부 등급인 것과 별개로 프로파일로 한 겹 더 막는다 — 등급은 무엇을 노출하지 않을지를
     * 선언할 뿐 아무것도 막지 않기 때문이다.
     */
    @RestController
    @RequestMapping("/internal/notifications/outbox")
    @Profile("local")
    static class OutboxController {
        private final NotificationUseCase notifications;

        OutboxController(NotificationUseCase notifications) {
            this.notifications = notifications;
        }

        @GetMapping
        List<OutboxEntryResponse> outbox() {
            return notifications.outbox().stream().map(NotificationResponseMapper::from).toList();
        }
    }
}
