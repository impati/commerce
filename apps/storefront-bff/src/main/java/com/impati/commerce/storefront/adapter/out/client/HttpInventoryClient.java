package com.impati.commerce.storefront.adapter.out.client;

import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.RestClientFactory;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.storefront.application.port.out.InventoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpInventoryClient implements InventoryClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpInventoryClient(
            RestClientFactory factory,
            ServiceCallExecutor calls,
            @Value("${clients.inventory.url}") String url
    ) {
        this.restClient = factory.forBaseUrl(url);
        this.calls = calls;
    }

    @Override
    public StockResponse get(String skuId) {
        return calls.query("storefront stock lookup", () -> restClient.get()
                .uri("/internal/stock/{id}", skuId)
                .retrieve()
                .body(StockResponse.class), error -> {
            if (error.hasCode("not_found")) {
                throw DomainException.notFound("재고 정보를 찾을 수 없습니다.");
            }
        });
    }
}
