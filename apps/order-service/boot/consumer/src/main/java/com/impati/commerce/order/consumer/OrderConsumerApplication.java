package com.impati.commerce.order.consumer;

import com.impati.commerce.order.adapter.out.persistence.JdbcOrderRepository;
import com.impati.commerce.order.application.component.OrderChanges;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackageClasses = {OrderConsumerApplication.class, JdbcOrderRepository.class})
@Import(OrderChanges.class)
public class OrderConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderConsumerApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
