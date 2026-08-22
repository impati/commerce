package com.impati.commerce.notification.adapter.out.mail;

import com.impati.commerce.notification.application.port.out.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 메일을 실제로 보내지 않고 로그로 남기는 로컬 대역.
 *
 * <p>운영에서는 SMTP나 벤더 API 어댑터로 교체한다. 애플리케이션과 도메인은 이 클래스의 존재를
 * 모르며, 외부 호출을 하지 않는다는 사실도 알지 못한다.
 */
@Component
public class LoggingMailSender implements MailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("mail to={} subject={}\n{}", recipient, subject, body);
    }
}
