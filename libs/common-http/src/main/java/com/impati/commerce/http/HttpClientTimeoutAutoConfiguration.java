package com.impati.commerce.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.RestClient;

/**
 * 모든 {@link RestClient}에 타임아웃을 건다. 서비스마다 설정을 반복하면 반드시 어긋나므로
 * 한 곳에서 자동 적용한다.
 *
 * <p>우선순위를 가장 높게 두어 이 커스터마이저가 먼저 적용되게 한다. 테스트에서
 * {@code MockServerRestClientCustomizer}가 요청 팩토리를 대체하는데, 그쪽이 나중에 적용돼야
 * 스텁이 살아남는다.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(HttpClientTimeoutProperties.class)
public class HttpClientTimeoutAutoConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    RestClientCustomizer httpClientTimeoutCustomizer(HttpClientTimeoutProperties properties) {
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(properties.readTimeout())
        ));
    }
}
