package com.impati.commerce.gateway.adapter.out.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 서비스별 {@link RestClient}를 한 곳에서 만든다.
 *
 * <p>어댑터마다 {@code RestClient.Builder}를 주입받으면 빌더 인스턴스가 어댑터 수만큼 생긴다.
 * 그러면 빌더 단위로 동작하는 테스트 스텁(MockServerRestClientCustomizer)이 여러 개로 갈라지고,
 * 커스터마이저로 거는 공통 정책도 어디에 걸렸는지 추적하기 어려워진다. 빌더는 한 번만 주입받아
 * 복제한다. 선례는 order-service의 {@code CommerceRestClients}다.
 *
 * <p>게이트웨이는 member-service를 두 목적으로 부른다 — 프록시({@link GatewayClients})와 세션
 * 확인({@code MemberIdentity})이다. 둘이 각자 빌더를 받고 있어서 이 문제가 실제로 났다.
 */
@Configuration
public class GatewayRestClients {
    private final RestClient.Builder builder;

    public GatewayRestClients(RestClient.Builder builder) {
        this.builder = builder;
    }

    @Bean
    RestClient memberRestClient(@Value("${clients.member.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient displayRestClient(@Value("${clients.display.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient catalogRestClient(@Value("${clients.catalog.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient inventoryRestClient(@Value("${clients.inventory.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient cartRestClient(@Value("${clients.cart.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient orderRestClient(@Value("${clients.order.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient shippingRestClient(@Value("${clients.shipping.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient notificationRestClient(@Value("${clients.notification.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }
}
