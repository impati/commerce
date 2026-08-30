package com.impati.commerce.test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import java.util.List;

/**
 * 스프링 컨텍스트마다 토픽과 컨슈머 그룹을 붙인다 (ADR-0016).
 *
 * <p><b>테스트가 아무것도 하지 않아도 적용된다.</b> {@code spring.factories}로 등록돼 있어 이
 * 모듈이 클래스패스에 있는 서비스의 모든 테스트 컨텍스트가 자기 토픽을 받는다.
 * {@code libs:test-database}가 데이터베이스에 하는 것과 같은 이유이며, 빠뜨렸을 때가 조용하기
 * 때문에 규율에 맡기지 않는다.
 */
public class KafkaContextCustomizerFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return new PerClassTopic(testClass);
    }

    /**
     * 테스트 클래스를 값으로 갖는다.
     *
     * <p>스프링은 이 객체의 동등성으로 컨텍스트를 캐시한다. 클래스가 다르면 다른 값이므로 컨텍스트가
     * 합쳐지지 않고, 그래서 토픽도 합쳐지지 않는다. 여기가 격리를 만드는 지점이다.
     */
    record PerClassTopic(Class<?> testClass) implements ContextCustomizer {
        @Override
        public void customizeContext(
                ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            var topic = TestKafka.createTopic(testClass.getSimpleName());
            TestPropertyValues.of(
                    "spring.kafka.bootstrap-servers=" + TestKafka.bootstrapServers(),
                    "commerce.kafka.order-events-topic=" + topic,
                    "spring.kafka.consumer.group-id=" + topic + "-consumers"
            ).applyTo(context);
        }
    }
}
