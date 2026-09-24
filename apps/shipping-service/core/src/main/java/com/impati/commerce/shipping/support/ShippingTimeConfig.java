package com.impati.commerce.shipping.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ShippingTimeConfig {
    @Bean
    Clock shippingClock() {
        return Clock.systemUTC();
    }
}
