package com.impati.commerce.notification.adapter.in.web;

import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.notification.application.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    List<NotificationResponse> list(@RequestParam String memberId) {
        return notifications.listFor(memberId);
    }
}
