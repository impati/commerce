package com.impati.commerce.notification.domain;

import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.Ids;

public final class NotificationModels {
    private NotificationModels() {
    }

    public static final class Notification {
        private final String id;
        private final String eventType;
        private final String memberId;
        private final String subject;
        private final String body;

        public Notification(String eventType, String memberId, String subject, String body) {
            this.id = Ids.newId("ntf");
            this.eventType = eventType;
            this.memberId = memberId;
            this.subject = subject;
            this.body = body;
        }

        public NotificationResponse toResponse() {
            return new NotificationResponse(id, eventType, memberId, subject, body);
        }
    }
}

