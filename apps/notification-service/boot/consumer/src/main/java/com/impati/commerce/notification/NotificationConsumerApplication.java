package com.impati.commerce.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 주문 사건을 받아 알림으로 적는 실행 단위 (ADR-0016).
 *
 * <p>api·worker와 나란한 세 번째 단위다. 절단면은 {@code adapter/in}의 종류이며 컨슈머가 세 번째
 * 종류다 — 컨트롤러는 api, 스케줄러는 worker, 브로커 구독은 여기다 (ADR-0014).
 *
 * <p>worker에 합치지 않는 이유는 <b>컨슈머가 메일을 보내지 않기 때문이다.</b> 여기서 하는 일은
 * 기록뿐이고 발송은 알림 아웃박스가 따로 가져간다. 합치면 이 프로세스가 쓰지도 않는 메일 벤더
 * 설정을 요구하게 된다 (ADR-0015). 스케일 축도 다르다 — 컨슈머는 파티션 수에, 발송기는 점유
 * 처리량에 맞춰 는다.
 */
@SpringBootApplication
public class NotificationConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationConsumerApplication.class, args);
    }
}
