package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class InMemoryNotificationRepository {
    private final List<Notification> notifications = new ArrayList<>();

    public synchronized void save(Notification notification) {
        notifications.add(notification);
    }

    public synchronized List<Notification> findAll() {
        return List.copyOf(notifications);
    }
}

