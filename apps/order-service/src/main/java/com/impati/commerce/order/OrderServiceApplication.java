package com.impati.commerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    /**
     * 시각을 주입해서 쓴다. 점유와 재시도 간격이 전부 시각 판정이므로, 주입하지 않으면
     * "실패한 뒤 간격이 지나기 전에는 다시 집히지 않는다"를 테스트가 고정할 수 없다.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
