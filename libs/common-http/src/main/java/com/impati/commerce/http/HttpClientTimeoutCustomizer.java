package com.impati.commerce.http;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

/** HTTP 시간 제한은 테스트 대역을 포함한 다른 커스터마이저보다 먼저 적용한다. */
final class HttpClientTimeoutCustomizer implements RestClientCustomizer, Ordered {
    private final HttpClientTimeoutProperties properties;

    HttpClientTimeoutCustomizer(HttpClientTimeoutProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void customize(RestClient.Builder builder) {
        customize(builder, properties.readTimeout());
    }

    void customize(RestClient.Builder builder, Duration readTimeout) {
        builder.requestFactory(ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(readTimeout)
        ));
    }
}
