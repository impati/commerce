package com.impati.commerce.inventory.support;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 재고 이동이 일어난 시각을 테스트에서 제어할 수 있도록 시간 원천을 주입한다. */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
