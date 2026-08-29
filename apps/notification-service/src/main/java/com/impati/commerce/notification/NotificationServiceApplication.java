package com.impati.commerce.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    /** 시간을 주입 가능하게 둔다. 점유와 재시도 간격이 전부 시각 판정이라 테스트가 제어해야 한다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

