package com.impati.commerce.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 알림 워커. 발송 아웃박스를 비우는 실행 단위다 (ADR-0014).
 *
 * <p>웹을 띄우는 것은 살아 있는지 보이게 하려는 것이다. 큐를 비우는 프로세스가 조용히 죽는 것을
 * 볼 수단이 없으면 발송이 멈춘 것을 사용자 문의로 알게 된다.
 *
 * <p>인스턴스를 몇 개 띄우든 같은 알림을 두 번 보내지 않는다. 그 성질은 저장소의 점유와 전송
 * 확인이 만들고 실행 단위 구성과 무관하다 (ADR-0011).
 */
@SpringBootApplication
@EnableScheduling
public class NotificationWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationWorkerApplication.class, args);
    }
}
