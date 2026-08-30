package com.impati.commerce.notification.adapter.out.mail;

import com.impati.commerce.notification.application.port.out.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메일을 실제로 보내지 않고 로그로 남기는 로컬 대역.
 *
 * <p>벤더를 붙이기 전까지 이 자리를 채운다. 발송 여부와 무관하게 아웃박스는 같은 흐름으로 돌고,
 * 어댑터만 갈아끼우면 실제 발송이 된다.
 */
@Component
public class LoggingMailSender implements MailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    /**
     * 수락한 키. 실제 벤더에서는 벤더의 저장소가 갖는 것을 여기서는 프로세스가 든다.
     *
     * <p>그래서 재시작하면 기억이 사라지고, 그 뒤의 재시도는 중복 발송이 된다. 로컬 대역의
     * 한계이며 규약의 한계가 아니다 — 실제 어댑터는 벤더가 답한다.
     */
    private final Set<String> accepted = ConcurrentHashMap.newKeySet();

    /** 수락을 먼저 남긴다. 로그가 실패해도 수락 사실이 사라지지 않아야 한다. */
    @Override
    public void send(String recipient, String subject, String body, String dispatchKey) {
        accepted.add(dispatchKey);
        log.info("mail to={} subject={} key={}\n{}", recipient, subject, dispatchKey, body);
    }

    @Override
    public boolean wasAccepted(String dispatchKey) {
        return accepted.contains(dispatchKey);
    }
}
