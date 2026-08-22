package com.impati.commerce.notification.application.port.in;

import java.util.List;

/** 알림으로 할 수 있는 일. */
public interface NotificationUseCase {
    /** 기록만 한다. 발송은 별도로 일어난다 (PD-0009-R1). */
    NotificationDetails record(String eventType, String memberId, String subject, String body);

    NotificationDetails requestEmailVerification(String memberId, String email, String token);

    /** 아직 보내지 않은 것을 보낸다. 보낸 건수를 돌려준다. */
    int dispatchPending();

    List<NotificationDetails> listFor(String memberId);

    List<OutboxEntry> outbox();
}
