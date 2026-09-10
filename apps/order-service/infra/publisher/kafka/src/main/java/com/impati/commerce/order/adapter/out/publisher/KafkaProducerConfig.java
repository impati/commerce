package com.impati.commerce.order.adapter.out.publisher;

import com.impati.commerce.common.ApiContracts.OrderEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

/**
 * 주문 사건 프로듀서의 시간 설정을 한 소스에서 만든다 (ADR-0017).
 *
 * <p>같은 값을 두 군데 적으면 반드시 어긋난다. {@link KafkaProducerTimeouts} 하나가 검증까지
 * 마친 세트를 들고, 그것이 자동 구성된 프로듀서(커스터마이저)와 발행 어댑터(send-timeout)에
 * 함께 흘러간다.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    KafkaProducerTimeouts kafkaProducerTimeouts(
            @Value("${commerce.kafka.linger:0ms}") Duration linger,
            @Value("${commerce.kafka.request-timeout:15s}") Duration requestTimeout,
            @Value("${commerce.kafka.delivery-timeout:15s}") Duration deliveryTimeout,
            @Value("${commerce.kafka.max-block:5s}") Duration maxBlock,
            @Value("${commerce.kafka.send-timeout:17s}") Duration sendTimeout) {
        return new KafkaProducerTimeouts(linger, requestTimeout, deliveryTimeout, maxBlock, sendTimeout);
    }

    /** 자동 구성된 프로듀서 팩토리에 검증된 시간 값을 싣는다. */
    @Bean
    DefaultKafkaProducerFactoryCustomizer orderEventProducerTimeouts(KafkaProducerTimeouts timeouts) {
        return factory -> factory.updateConfigs(timeouts.producerConfig());
    }

    @Bean
    KafkaOrderEventPublisher kafkaOrderEventPublisher(
            KafkaTemplate<String, OrderEventMessage> kafkaTemplate,
            @Value("${commerce.kafka.order-events-topic}") String topic,
            KafkaProducerTimeouts timeouts) {
        return new KafkaOrderEventPublisher(kafkaTemplate, topic, timeouts.sendTimeout());
    }
}
