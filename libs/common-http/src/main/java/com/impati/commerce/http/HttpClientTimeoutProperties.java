package com.impati.commerce.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 서비스 간 HTTP 호출의 타임아웃.
 *
 * <p>기본값을 두는 이유는 설정을 빠뜨렸을 때 무한 대기로 돌아가지 않게 하는 것이다.
 * 타임아웃이 없으면 상대 서비스가 응답을 멈출 때 호출자의 스레드가 그대로 잡힌다.
 */
@ConfigurationProperties(prefix = "clients.http")
public record HttpClientTimeoutProperties(Duration connectTimeout, Duration readTimeout) {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(3);

    public HttpClientTimeoutProperties {
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }
}
