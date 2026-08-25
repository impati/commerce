package com.impati.commerce.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class MemberServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }

    /** 시간을 주입 가능하게 둔다. 만료 검사를 테스트에서 제어할 수 있어야 한다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
