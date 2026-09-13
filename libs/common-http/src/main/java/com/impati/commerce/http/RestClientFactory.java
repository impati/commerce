package com.impati.commerce.http;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.web.client.RestClient;

/** 모든 서비스 간 HTTP 클라이언트를 하나의 커스터마이징된 빌더에서 만든다. */
public final class RestClientFactory {
    private final RestClient.Builder builder;

    RestClientFactory(ObjectProvider<RestClientCustomizer> customizers) {
        this.builder = RestClient.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
    }

    public RestClient forBaseUrl(String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }
}
