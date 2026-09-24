package com.impati.commerce.shipping.adapter.out.publisher;

import com.impati.commerce.common.ApiContracts.ShipmentEventMessage;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class ShipmentKafkaConfig {
    @Bean
    DefaultKafkaProducerFactoryCustomizer shipmentProducerTimeouts(
            @Value("${commerce.kafka.max-block:5s}") Duration maxBlock) {
        return factory -> factory.updateConfigs(Map.of(
                "max.block.ms", maxBlock.toMillis(),
                "request.timeout.ms", 15_000,
                "delivery.timeout.ms", 15_000));
    }

    @Bean
    KafkaShipmentEventPublisher shipmentEventPublisher(
            KafkaTemplate<String, ShipmentEventMessage> template,
            @Value("${commerce.kafka.shipment-events-topic}") String topic,
            @Value("${commerce.kafka.send-timeout:17s}") Duration timeout) {
        return new KafkaShipmentEventPublisher(template, topic, timeout);
    }

    @Bean
    NewTopic shipmentEventsTopic(
            @Value("${commerce.kafka.shipment-events-topic}") String topic,
            @Value("${commerce.kafka.shipment-events-partitions:3}") int partitions,
            @Value("${commerce.kafka.shipment-events-replicas:3}") short replicas,
            @Value("${commerce.kafka.shipment-events-min-insync-replicas:2}") String minInsyncReplicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas)
                .configs(Map.of("min.insync.replicas", minInsyncReplicas)).build();
    }
}
