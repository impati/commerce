package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.http.RestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * inventory-service를 부르는 {@link RestClient}. 이 모듈을 의존하는 실행 단위만 이 빈을 갖는다.
 *
 * <p>URL 설정도 여기서만 요구한다. 부르지 않는 실행 단위는 이 모듈을 의존하지 않으므로
 * {@code clients.inventory.url}이 없어도 뜬다 (ADR-0015).
 */
@Configuration
class InventoryRestClientConfig {
    @Bean
    RestClient inventoryRestClient(RestClientFactory restClients, @Value("${clients.inventory.url}") String baseUrl) {
        return restClients.forBaseUrl(baseUrl);
    }
}
