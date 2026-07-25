package com.impati.commerce.notification.application;

import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public NotificationResponse record(String eventType, String memberId, String subject, String body) {
        var notification = new Notification(eventType, memberId, subject, body);
        notifications.save(notification);
        return notification.toResponse();
    }

    public List<NotificationResponse> list() {
        return notifications.findAll().stream().map(Notification::toResponse).toList();
    }
}

