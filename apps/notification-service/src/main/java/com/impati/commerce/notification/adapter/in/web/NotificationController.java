package com.impati.commerce.notification.adapter.in.web;

import com.impati.commerce.common.ApiContracts.EmailVerificationMailRequest;
import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.ApiContracts.OutboxEntryResponse;
import com.impati.commerce.notification.application.NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/events")
    NotificationResponse record(@RequestBody NotificationEventRequest request) {
        return notifications.record(request.eventType(), request.memberId(), request.subject(), request.body());
    }

    @PostMapping("/email-verifications")
    NotificationResponse requestEmailVerification(@RequestBody EmailVerificationMailRequest request) {
        return notifications.requestEmailVerification(request.memberId(), request.email(), request.token());
    }

    @GetMapping
    List<NotificationResponse> list(@org.springframework.web.bind.annotation.RequestParam String memberId) {
        return notifications.listFor(memberId);
    }

    /**
     * 발송함 조회. {@code local} 프로파일에서만 존재한다.
     *
     * <p>메일 본문에는 인증 토큰이 들어 있다. 운영에서 이 경로가 열려 있으면 남의 계정을 인증해
     * 가로챌 수 있다. 프로파일이 없으면 이 컨트롤러 자체가 빈으로 만들어지지 않는다.
     */
    @RestController
    @RequestMapping("/notifications/outbox")
    @Profile("local")
    static class OutboxController {
        private final NotificationService notifications;

        OutboxController(NotificationService notifications) {
            this.notifications = notifications;
        }

        @GetMapping
        List<OutboxEntryResponse> outbox() {
            return notifications.outbox();
        }
    }
}
