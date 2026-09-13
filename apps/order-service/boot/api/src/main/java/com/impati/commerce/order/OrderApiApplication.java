package com.impati.commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;

/**
 * 주문 API. 사용자 요청을 받는 실행 단위다 (ADR-0014).
 *
 * <p>{@code @EnableScheduling}이 없다. 스케줄러는 워커의 것이고, 이 모듈의 클래스패스에는
 * 스케줄러 코드가 아예 없다 — 켜고 끄는 문제가 아니라 있고 없고의 문제로 만든 것이 이 분리의
 * 요점이다.
 */
@SpringBootApplication(exclude = RestClientAutoConfiguration.class)
public class OrderApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApiApplication.class, args);
    }
}
