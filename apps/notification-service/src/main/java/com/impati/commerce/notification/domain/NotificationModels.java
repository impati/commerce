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

        /**
         * 발신자가 부여한 중복 판정 키. 같은 키의 알림은 하나만 존재한다 (ADR-0011).
         *
         * <p>기록만 남기는 알림은 아직 키가 없어 {@code null}이다. 없는 것을 임의의 값으로
         * 채우지 않는다 — 랜덤 키는 유니크 제약을 통과하므로 멱등한 척하면서 아무것도 거르지
         * 못한다. 주문 알림 경로에 키를 붙이는 것은 별도 항목이다.
         */
        private final String idempotencyKey;

        private final String eventType;
        private final String memberId;
        private final String subject;
        private final String body;
        private final Channel channel;
        private final String recipient;
        private DeliveryStatus deliveryStatus;
        private int attempts;
        private String lastError;

        /** 기록만 남기는 알림. 중복 판정 대상이 아니므로 키가 없다. */
        public Notification(String eventType, String memberId, String subject, String body) {
            this(Ids.newId("ntf"), null, eventType, memberId, subject, body,
                    Channel.NONE, null, DeliveryStatus.SKIPPED, 0, null);
        }

        /**
         * 메일로 보내야 하는 알림. 기록 시점에는 PENDING이고 발송은 분리된다.
         *
         * <p>멱등 키를 요구한다. 없으면 거절하는 이유는, 선택값으로 두면 키를 빠뜨린 호출자가
         * 조용히 중복 발송으로 돌아가고 그 사실이 사용자에게 메일이 두 통 도착할 때까지
         * 드러나지 않기 때문이다 (ADR-0011).
         */
        public static Notification mail(
                String eventType,
                String memberId,
                String recipient,
                String subject,
                String body,
                String idempotencyKey
        ) {
            if (recipient == null || recipient.isBlank()) {
                throw DomainException.validation("mail recipient is required");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw DomainException.validation("idempotency key is required");
            }
            return new Notification(
                    Ids.newId("ntf"),
                    idempotencyKey,
                    eventType,
                    memberId,
                    subject,
                    body,
                    Channel.MAIL,
                    recipient,
                    DeliveryStatus.PENDING,
                    0,
                    null
            );
        }

        private Notification(
                String id,
                String idempotencyKey,
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
            this.idempotencyKey = idempotencyKey;
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
                String idempotencyKey,
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
            return new Notification(id, idempotencyKey, eventType, memberId, subject, body,
                    channel, recipient, deliveryStatus, attempts, lastError);
        }

        public String id() {
            return id;
        }

        public String idempotencyKey() {
            return idempotencyKey;
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

        public boolean isSent() {
            return deliveryStatus == DeliveryStatus.SENT;
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
