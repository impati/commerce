package com.impati.commerce.notification.application.port.in;

import java.util.List;

/**
 * 알림을 받고 조회한다.
 *
 * <p>보내는 것은 {@link MailDispatchUseCase}가 갖는다. 받는 것과 보내는 것은 서로 다른 실행
 * 단위에서 돌고(ADR-0014), 보내는 쪽만 메일 벤더를 알아야 하기 때문이다 (ADR-0015).
 */
public interface NotificationUseCase {
    /**
     * 기록만 한다. 발송은 별도로 일어난다 (PD-0009-R1).
     *
     * <p>{@code idempotencyKey}가 같은 요청이 다시 오면 새로 적지 않고 먼저 적힌 것을
     * 돌려준다 (ADR-0012). 그래서 부르는 쪽은 결과를 모를 때 마음 놓고 다시 부를 수 있다.
     */
    NotificationDetails record(
            String eventType, String memberId, String subject, String body, String idempotencyKey);

    /**
     * 인증 메일을 아웃박스에 적는다.
     *
     * <p>{@code idempotencyKey}가 같은 요청이 다시 오면 새로 적지 않고 먼저 적힌 것을
     * 돌려준다 (ADR-0011). 그래서 부르는 쪽은 결과를 모를 때 마음 놓고 다시 부를 수 있다.
     */
    NotificationDetails requestEmailVerification(String memberId, String email, String token, String idempotencyKey);

    List<NotificationDetails> listFor(String memberId);

    List<OutboxEntry> outbox();
}
