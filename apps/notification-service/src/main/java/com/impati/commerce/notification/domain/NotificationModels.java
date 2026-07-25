package com.impati.commerce.notification.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

public final class NotificationModels {
    private NotificationModels() {
    }

    /** 발송 채널. NONE은 기록만 남기고 외부로 보내지 않는 알림이다. */
    public enum Channel {
        NONE, MAIL
    }

    /**
     * 발송 상태.
     *
     * <p>SKIPPED는 발송 대상이 아닌 기록이다. PENDING은 보내야 하는데 아직 못 보낸 것이고,
     * 이 상태가 남아 있는 것 자체가 관측 대상이다. 실패를 삼키지 않기 위해 FAILED를 따로 둔다.
     */
    public enum DeliveryStatus {
        SKIPPED, PENDING, SENT, FAILED
    }

    /**
     * 알림 한 건. 발송이 필요한 알림은 이 레코드가 아웃박스 항목이 된다.
     *
     * <p>본문을 기록 시점에 렌더링해 보관한다. 발송이 나중에 일어나도 그때의 템플릿 변경에
     * 영향받지 않고, 무엇을 보냈는지가 남는다.
     */
    public static final class Notification {
        private final String id;
        private final String eventType;
        private final String memberId;
        private final String subject;
        private final String body;
        private final Channel channel;
        private final String recipient;
        private DeliveryStatus deliveryStatus;
        private int attempts;
        private String lastError;

        /** 기록만 남기는 알림. */
        public Notification(String eventType, String memberId, String subject, String body) {
            this(Ids.newId("ntf"), eventType, memberId, subject, body,
                    Channel.NONE, null, DeliveryStatus.SKIPPED, 0, null);
        }

        /** 메일로 보내야 하는 알림. 기록 시점에는 PENDING이고 발송은 분리된다. */
        public static Notification mail(
                String eventType,
                String memberId,
                String recipient,
                String subject,
                String body
        ) {
            if (recipient == null || recipient.isBlank()) {
                throw DomainException.validation("mail recipient is required");
            }
            return new Notification(Ids.newId("ntf"), eventType, memberId, subject, body,
                    Channel.MAIL, recipient, DeliveryStatus.PENDING, 0, null);
        }

        private Notification(
                String id,
                String eventType,
                String memberId,
                String subject,
                String body,
                Channel channel,
                String recipient,
                DeliveryStatus deliveryStatus,
                int attempts,
                String lastError
        ) {
            this.id = id;
            this.eventType = eventType;
            this.memberId = memberId;
            this.subject = subject;
            this.body = body;
            this.channel = channel;
            this.recipient = recipient;
            this.deliveryStatus = deliveryStatus;
            this.attempts = attempts;
            this.lastError = lastError;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static Notification restore(
                String id,
                String eventType,
                String memberId,
                String subject,
                String body,
                Channel channel,
                String recipient,
                DeliveryStatus deliveryStatus,
                int attempts,
                String lastError
        ) {
            return new Notification(id, eventType, memberId, subject, body,
                    channel, recipient, deliveryStatus, attempts, lastError);
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

        public Channel channel() {
            return channel;
        }

        public String recipient() {
            return recipient;
        }

        public DeliveryStatus deliveryStatus() {
            return deliveryStatus;
        }

        public int attempts() {
            return attempts;
        }

        public String lastError() {
            return lastError;
        }

        public void markSent() {
            this.attempts += 1;
            this.deliveryStatus = DeliveryStatus.SENT;
            this.lastError = null;
        }

        /** 실패를 기록으로 남긴다. 재시도는 PENDING으로 되돌려 다음 주기에 다시 집는다. */
        public void markFailed(String error, int maxAttempts) {
            this.attempts += 1;
            this.lastError = error;
            this.deliveryStatus = attempts >= maxAttempts ? DeliveryStatus.FAILED : DeliveryStatus.PENDING;
        }
    }
}
