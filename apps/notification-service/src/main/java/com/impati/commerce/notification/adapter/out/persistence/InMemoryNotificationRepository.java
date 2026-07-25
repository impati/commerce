package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.application.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class InMemoryNotificationRepository implements NotificationRepository {
    private final List<Notification> notifications = new ArrayList<>();

    @Override
    public synchronized void save(Notification notification) {
        notifications.add(notification);
    }

    @Override
    public synchronized List<Notification> findAll() {
        return List.copyOf(notifications);
    }
}
