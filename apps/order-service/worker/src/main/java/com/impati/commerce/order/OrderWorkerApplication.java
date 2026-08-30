package com.impati.commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주문 워커. 결제 미확인 정리와 사건 발행을 돌리는 실행 단위다 (ADR-0014).
 *
 * <p>웹을 띄운다. 필요해서가 아니라 <b>살아 있는지 보이게 하려는 것</b>이다 — 큐를 비우는
 * 프로세스가 조용히 죽는 것은 이 저장소가 반복해서 문제 삼은 실패 모양이고, 기동 확인도
 * {@code /actuator/health}에 걸려 있다.
 *
 * <p>인스턴스를 몇 개 띄우든 같은 작업을 두 번 하지 않는다. 그 성질은 저장소의 점유가 만들고
 * 실행 단위 구성과 무관하다 (ADR-0011, ADR-0012).
 */
@SpringBootApplication
@EnableScheduling
public class OrderWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderWorkerApplication.class, args);
    }
}
