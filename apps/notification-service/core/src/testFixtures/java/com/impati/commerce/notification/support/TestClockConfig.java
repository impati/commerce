package com.impati.commerce.notification.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 시계를 테스트가 제어하는 것으로 바꾼다. 애플리케이션의 시스템 시계보다 우선한다. */
@TestConfiguration
public class TestClockConfig {
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }
}
