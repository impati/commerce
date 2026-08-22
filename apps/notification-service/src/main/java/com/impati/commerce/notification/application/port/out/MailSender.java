package com.impati.commerce.notification.application.port.out;

/**
 * 메일 발송 포트. 구현은 {@code adapter/out/mail}에 둔다.
 *
 * <p>렌더링된 본문을 받는다. 아웃박스에 본문을 저장해 두므로 발송 시점에 다시 렌더링할 것이 없다.
 * 벤더 템플릿 기능을 쓰게 되면 아웃박스에 템플릿 키와 변수를 저장하고 이 포트를 그에 맞게 바꾼다.
 * 어느 쪽이든 벤더 개념은 어댑터 밖으로 나오지 않는다.
 */
public interface MailSender {
    void send(String recipient, String subject, String body);
}
