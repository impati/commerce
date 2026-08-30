package com.impati.commerce.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 알림 API. 형제 서비스의 알림 요청을 받는 실행 단위다 (ADR-0014).
 *
 * <p>{@code @EnableScheduling}이 없다. 발송기는 워커의 것이고 이 모듈의 클래스패스에는 그
 * 코드가 없다.
 *
 * <p>카프카가 들어오면 컨슈머가 이 서비스에 생기는데, 그것도 워커로 간다 — 사용자 요청을 받는
 * 프로세스가 브로커를 알아야 할 이유가 없다 (BL-0055).
 */
@SpringBootApplication
public class NotificationApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApiApplication.class, args);
    }
}
