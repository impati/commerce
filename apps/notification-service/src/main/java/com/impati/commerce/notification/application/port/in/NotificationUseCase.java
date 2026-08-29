package com.impati.commerce.notification.application.port.in;

import java.util.List;

/** 알림으로 할 수 있는 일. */
public interface NotificationUseCase {
    /** 기록만 한다. 발송은 별도로 일어난다 (PD-0009-R1). */
    NotificationDetails record(String eventType, String memberId, String subject, String body);

    /**
     * 인증 메일을 아웃박스에 적는다.
     *
     * <p>{@code idempotencyKey}가 같은 요청이 다시 오면 새로 적지 않고 먼저 적힌 것을
     * 돌려준다 (ADR-0011). 그래서 부르는 쪽은 결과를 모를 때 마음 놓고 다시 부를 수 있다.
     */
    NotificationDetails requestEmailVerification(String memberId, String email, String token, String idempotencyKey);

    /** 아직 보내지 않은 것을 보낸다. 이번 주기에 발송이 확정된 건수를 돌려준다. */
    int dispatchPending();

    List<NotificationDetails> listFor(String memberId);

    List<OutboxEntry> outbox();
}
