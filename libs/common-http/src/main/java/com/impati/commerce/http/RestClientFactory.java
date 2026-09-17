package com.impati.commerce.http;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.web.client.RestClient;

/** 모든 서비스 간 HTTP 클라이언트를 하나의 커스터마이징된 빌더에서 만든다. */
public final class RestClientFactory {
    private final RestClient.Builder builder;
    private final List<RestClientCustomizer> customizers;

    RestClientFactory(ObjectProvider<RestClientCustomizer> customizers) {
        this.customizers = customizers.orderedStream().toList();
        this.builder = RestClient.builder();
        this.customizers.forEach(customizer -> customizer.customize(builder));
    }

    public RestClient forBaseUrl(String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    /** 경로별 읽기 제한을 적용해도 테스트 대역과 공통 커스터마이저의 순서는 유지한다. */
    public RestClient forBaseUrl(String baseUrl, Duration readTimeout) {
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("read timeout must be positive");
        }
        var routeBuilder = RestClient.builder();
        for (var customizer : customizers) {
            if (customizer instanceof HttpClientTimeoutCustomizer timeoutCustomizer) {
                timeoutCustomizer.customize(routeBuilder, readTimeout);
            } else {
                customizer.customize(routeBuilder);
            }
        }
        return routeBuilder.baseUrl(baseUrl).build();
    }
}
