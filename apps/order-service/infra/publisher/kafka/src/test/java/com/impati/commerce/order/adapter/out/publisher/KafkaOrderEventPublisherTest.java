package com.impati.commerce.order.adapter.out.publisher;

import com.impati.commerce.common.ApiContracts.OrderEventMessage;
import com.impati.commerce.order.application.port.out.OrderEventPublisher;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import com.impati.commerce.order.domain.OrderModels.PublishStatus;
import com.impati.commerce.test.RequiresKafka;
import com.impati.commerce.test.TestKafka;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사건이 브로커에 어떤 모양으로, 어느 파티션에, 어떤 순서로 실리는지 확인한다 (ADR-0016).
 *
 * <p>릴레이 테스트는 발행 포트를 대역으로 쓰므로 이 성질을 잡지 못한다. 여기서만 실제 브로커가
 * 돌고, 여기서만 <b>같은 키가 같은 파티션에 떨어지는지</b>를 확인할 수 있다 — 그것이 순서 보장이
 * 서 있는 전제다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다. 검증 대상이 이 어댑터 하나이고, 컨텍스트를 띄우면 이 모듈이
 * 담지 않은 빈들까지 요구하게 된다. 브로커는 대역이 아니라 실물이다.
 */
@RequiresKafka
class KafkaOrderEventPublisherTest {

    private OrderEventPublisher orderEventPublisher;
    private String topic;
    private final String bootstrapServers = TestKafka.bootstrapServers();

    @BeforeEach
    void setUp() {
        topic = TestKafka.createTopic("publisher");
        orderEventPublisher = new KafkaOrderEventPublisher(template(), topic, Duration.ofSeconds(10));
    }

    /**
     * 운영 설정 그대로 만든다 — {@code acks=all}과 프로듀서 멱등.
     *
     * <p>{@code min.insync.replicas}는 토픽 쪽 설정이라 여기서 정하지 않는다. 테스트 브로커는
     * 노드가 하나라 복제가 유실을 막는지는 여기서 확인되지 않는다.
     */
    private KafkaTemplate<String, OrderEventMessage> template() {
        var config = new HashMap<String, Object>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("acks", "all");
        config.put("enable.idempotence", true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                config, new org.apache.kafka.common.serialization.StringSerializer(), new JsonSerializer<>()));
    }

    /**
     * 도메인 사건이 계약 타입으로 옮겨진다. 문구는 담기지 않는다 — 그것은 소비자의 일이다.
     */
    @Test
    void publishesTheFactNotTheWording() {
        var event = event(OrderEventType.ORDER_PAID, "ord_pub_1", "mem_pub_1", Map.of());

        orderEventPublisher.publish(event);

        var records = drain(1);
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.key()).isEqualTo("ord_pub_1");
            assertThat(record.value().eventId()).isEqualTo(event.id());
            assertThat(record.value().type()).isEqualTo("ORDER_PAID");
            assertThat(record.value().memberId()).isEqualTo("mem_pub_1");
        });
    }

    /**
     * 사건별 사실이 그대로 실린다. 소비자가 문구를 만들 때 쓰는 값이다.
     */
    @Test
    void carriesThePayload() {
        orderEventPublisher.publish(event(
                OrderEventType.SHIPMENT_CREATED, "ord_pub_2", "mem_pub_2",
                Map.of("trackingNumber", "TRK-PUB")));

        assertThat(drain(1)).singleElement()
                .satisfies(record ->
                        assertThat(record.value().payload()).containsEntry("trackingNumber", "TRK-PUB"));
    }

    /**
     * 같은 주문의 사건이 한 파티션에 넣은 순서로 실린다.
     *
     * <p>파티션 키가 주문 id이기 때문이고, 이것이 깨지면 릴레이가 순서대로 넘겨도 소비자가 보는
     * 순서가 달라진다. 토픽에 파티션이 여럿이라 키가 없으면 흩어진다.
     */
    @Test
    void theSameOrderKeepsItsOrderOnOnePartition() {
        orderEventPublisher.publish(event(OrderEventType.ORDER_CREATED, "ord_pub_3", "mem_pub_3", Map.of()));
        orderEventPublisher.publish(event(OrderEventType.ORDER_PAID, "ord_pub_3", "mem_pub_3", Map.of()));
        orderEventPublisher.publish(event(OrderEventType.ORDER_DELIVERED, "ord_pub_3", "mem_pub_3", Map.of()));

        var records = drain(3);

        // 키부터 본다. 파티션이 같은 것은 우연히도 성립할 수 있지만 — 키가 셋이어도 한 파티션에
        // 몰릴 수 있다 — 키가 주문 id인 것은 우연이 아니다.
        assertThat(records).extracting(record -> record.key()).containsOnly("ord_pub_3");
        assertThat(records).extracting(record -> record.partition()).containsOnly(records.get(0).partition());
        assertThat(records).extracting(record -> record.value().type())
                .containsExactly("ORDER_CREATED", "ORDER_PAID", "ORDER_DELIVERED");
    }

    private OrderEvent event(OrderEventType type, String orderId, String memberId, Map<String, String> payload) {
        return OrderEvent.restore(
                "evt_" + type.name().toLowerCase() + "_" + orderId,
                type, orderId, memberId, payload, PublishStatus.PENDING, 0, null, LocalDateTime.now());
    }

    /**
     * 토픽이 이 컨텍스트 전용이므로 처음부터 읽어 원하는 수만큼 모은다.
     */
    private List<ConsumerRecord<String, OrderEventMessage>> drain(int expected) {
        try (var consumer = consumer()) {
            consumer.subscribe(List.of(topic));
            var collected = new ArrayList<ConsumerRecord<String, OrderEventMessage>>();
            var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (collected.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofSeconds(1)).forEach(collected::add);
            }
            assertThat(collected).as("브로커에서 %d건을 읽지 못했다", expected).hasSize(expected);
            return collected;
        }
    }

    private KafkaConsumer<String, OrderEventMessage> consumer() {
        var deserializer = new JsonDeserializer<>(OrderEventMessage.class);
        deserializer.addTrustedPackages("com.impati.commerce.common");
        return new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, topic + "-assertions",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
                ),
                new StringDeserializer(),
                deserializer);
    }
}
