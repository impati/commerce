package com.impati.commerce.notification.domain;

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
            this(Ids.newId("ntf"), eventType, memberId, subject, body);
        }

        private Notification(String id, String eventType, String memberId, String subject, String body) {
            this.id = id;
            this.eventType = eventType;
            this.memberId = memberId;
            this.subject = subject;
            this.body = body;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static Notification restore(
                String id,
                String eventType,
                String memberId,
                String subject,
                String body
        ) {
            return new Notification(id, eventType, memberId, subject, body);
        }

        public String id() {
            return id;
        }

        public String eventType() {
            return eventType;
        }

        public String memberId() {
            return memberId;
        }

        public String subject() {
            return subject;
        }

        public String body() {
            return body;
        }
    }
}
