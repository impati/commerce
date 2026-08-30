package com.impati.commerce.test;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 격리와 순서를 만드는 성질을 고정한다 (ADR-0016).
 *
 * <p>테스트가 서로의 사건을 집어가지 않는 근거, 그리고 순서 검증이 우연히 통과하지 않는 근거가
 * 여기다.
 */
@RequiresKafka
class TestKafkaTest {
    @Test
    void sameNameStillGetsItsOwnTopic() {
        var first = TestKafka.createTopic("same-name");
        var second = TestKafka.createTopic("same-name");

        assertThat(first).isNotEqualTo(second);
    }

    /**
     * 토픽이 파티션을 여럿 갖는다.
     *
     * <p>하나면 키가 달라도 전부 한 곳에 떨어져 <b>순서가 저절로 지켜진다.</b> 그 위에서 도는
     * 순서 테스트는 릴레이가 순서를 깨도 통과하므로 검증이 아니다.
     */
    @Test
    void topicHasMoreThanOnePartition() {
        var topic = TestKafka.createTopic("partitioned");

        try (var consumer = consumer()) {
            assertThat(consumer.partitionsFor(topic))
                    .as("파티션이 하나면 순서 테스트가 항상 통과한다")
                    .hasSizeGreaterThan(1);
        }
    }

    /** 같은 키로 보낸 것은 같은 파티션에 떨어진다. 순서 보장이 서 있는 전제다. */
    @Test
    void sameKeyLandsOnTheSamePartition() {
        var topic = TestKafka.createTopic("same-key");

        try (var producer = producer()) {
            var first = producer.send(new ProducerRecord<>(topic, "ord_1", "a"));
            var second = producer.send(new ProducerRecord<>(topic, "ord_1", "b"));
            producer.flush();

            assertThat(first.get().partition()).isEqualTo(second.get().partition());
        } catch (Exception failure) {
            throw new IllegalStateException("produce failed", failure);
        }
    }

    /** 새로 받은 토픽은 비어 있다. 지난 실행이 남긴 것을 물려받지 않는다. */
    @Test
    void handedTopicIsEmpty() {
        var topic = TestKafka.createTopic("leftover");

        try (var consumer = consumer()) {
            consumer.subscribe(List.of(topic));
            assertThat(consumer.poll(Duration.ofSeconds(2)).count()).isZero();
        }
    }

    private KafkaProducer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestKafka.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()
        ));
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestKafka.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-kafka-self-check",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ));
    }
}
