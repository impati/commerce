package com.impati.commerce.order.adapter.out.publisher;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * 주문 사건 토픽의 모양을 선언한다 (ADR-0016).
 *
 * <p>사건 스트림을 소유한 서비스가 그 토픽의 설정도 갖는다. 브로커 기본값에 맡기면 파티션이
 * 하나가 되어 <b>키와 무관하게 전부 한 곳에 떨어지고</b>, 복제가 하나가 되어 유실을 막는 장치가
 * 사라진다. 둘 다 조용히 잘못 도는 실패다.
 *
 * <p><b>기본값이 운영 값이다.</b> 로컬은 브로커가 하나라 이 값을 쓸 수 없어 설정으로 내리지만,
 * 그것을 기본으로 삼지 않는다 — 기본이 약하면 올리는 것을 잊는 순간 조용히 유실된다.
 *
 * <p>{@code min.insync.replicas}가 없는 {@code acks=all}은 거짓 안전이다. {@code acks=all}은
 * "ISR 전부"라는 뜻이라 ISR이 하나로 줄면 {@code acks=1}과 같아지고, 그 하나가 죽으면 사라진다.
 * 2로 두면 브로커 하나를 잃어도 데이터가 남고 둘을 잃으면 쓰기가 거절된다 — 거절은 릴레이가
 * 재시도하므로 유실이 아니다.
 *
 * <p><b>파티션 수는 나중에 늘릴 수 없다.</b> 늘리면 키에서 파티션으로 가는 매핑이 바뀌어 같은
 * 주문이 다른 파티션으로 가고, 그 시점에 순서가 한 번 깨진다.
 *
 * <p>클래스 이름에 {@code Config}가 붙은 이유는 스프링이 설정 클래스를 <b>이름을 소문자로 낮춘
 * 빈</b>으로 등록하기 때문이다. {@code @Bean} 메서드와 이름이 같으면 같은 빈을 두 번 등록하려다
 * 컨텍스트가 뜨지 않는다.
 */
@Configuration
public class OrderEventsTopicConfig {

    @Bean
    NewTopic orderEventsTopic(
            @Value("${commerce.kafka.order-events-topic}") String topic,
            @Value("${commerce.kafka.order-events-partitions:3}") int partitions,
            @Value("${commerce.kafka.order-events-replicas:3}") short replicas,
            @Value("${commerce.kafka.order-events-min-insync-replicas:2}") String minInsyncReplicas
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .configs(Map.of("min.insync.replicas", minInsyncReplicas))
                .build();
    }
}
