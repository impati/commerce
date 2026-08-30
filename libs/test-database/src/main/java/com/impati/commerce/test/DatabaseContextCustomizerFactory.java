package com.impati.commerce.test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import java.util.List;

/**
 * 스프링 컨텍스트마다 데이터베이스를 붙인다 (ADR-0013).
 *
 * <p><b>테스트가 아무것도 하지 않아도 적용된다.</b> {@code spring.factories}로 등록돼 있어 이
 * 모듈이 클래스패스에 있는 서비스의 모든 테스트 컨텍스트가 자기 데이터베이스를 받는다.
 *
 * <p>테스트가 직접 부르게 두지 않는 이유는 <b>빠뜨렸을 때가 조용하기 때문이다.</b> 붙이지 않으면
 * {@code application.properties}의 기본값으로 떨어져 개발자의 로컬 개발 DB에 연결되고, 그 테스트는
 * 실패하는 대신 실제 데이터를 건드린다. 규율에 맡길 수 없는 종류다.
 */
public class DatabaseContextCustomizerFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return new PerClassDatabase(testClass);
    }

    /**
     * 테스트 클래스를 값으로 갖는다.
     *
     * <p>스프링은 이 객체의 동등성으로 컨텍스트를 캐시한다. 클래스가 다르면 다른 값이므로 컨텍스트가
     * 합쳐지지 않고, 그래서 데이터베이스도 합쳐지지 않는다. 여기가 격리를 만드는 지점이다.
     */
    record PerClassDatabase(Class<?> testClass) implements ContextCustomizer {
        @Override
        public void customizeContext(
                ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + TestDatabase.createDatabase(testClass.getSimpleName()),
                    "spring.datasource.username=" + TestDatabase.username(),
                    "spring.datasource.password=" + TestDatabase.password()
            ).applyTo(context);
        }
    }
}
