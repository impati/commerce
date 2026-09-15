package com.impati.commerce.cart.adapter.out.client;

import com.impati.commerce.cart.application.port.out.CatalogClient;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.RestClientFactory;
import com.impati.commerce.http.ServiceCallExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpCatalogClient(
            RestClientFactory restClients,
            ServiceCallExecutor calls,
            @Value("${clients.catalog.url}") String baseUrl
    ) {
        this.restClient = restClients.forBaseUrl(baseUrl);
        this.calls = calls;
    }

    @Override
    public SkuResponse getSku(String skuId) {
        return calls.query("cart sku lookup", () -> restClient.get()
                .uri("/internal/skus/{skuId}", skuId)
                .retrieve()
                .body(SkuResponse.class), error -> {
            if (error.hasCode("not_found")) {
                throw DomainException.notFound("sku not found");
            }
        });
    }
}
