package com.impati.commerce.notification.application.port.in;

/**
 * 아직 보내지 않은 알림을 보낸다.
 *
 * <p>받는 것과 나뉜 이유는 둘이 다른 실행 단위에서 돌기 때문이다 (ADR-0014). 그리고 나뉘어야
 * 메일 벤더를 아는 것이 보내는 쪽 하나로 좁혀진다 — 한 클래스가 겸하면 받는 쪽 컨텍스트도
 * {@code MailSender} 빈을 요구한다 (ADR-0015).
 */
public interface MailDispatchUseCase {
    /** 이번 주기에 발송이 확정된 건수를 돌려준다. */
    int dispatchPending();
}
