package com.impati.commerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /** 접근 토큰의 만료를 판정할 때 쓴다. 테스트가 시간을 고정할 수 있도록 빈으로 둔다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
