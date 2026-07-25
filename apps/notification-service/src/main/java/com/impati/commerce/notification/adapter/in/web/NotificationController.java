package com.impati.commerce.notification.adapter.in.web;

import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.notification.application.NotificationService;
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

    @GetMapping
    List<NotificationResponse> list() {
        return notifications.list();
    }
}

