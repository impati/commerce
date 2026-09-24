package com.impati.commerce.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 택배 접수 복구와 배송 사건 발행을 맡는 실행 단위 (ADR-0029). */
@SpringBootApplication(exclude = RestClientAutoConfiguration.class)
@EnableScheduling
public class ShippingWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingWorkerApplication.class, args);
    }
}
