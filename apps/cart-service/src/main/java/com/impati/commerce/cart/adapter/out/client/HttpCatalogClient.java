package com.impati.commerce.cart.adapter.out.client;

import com.impati.commerce.cart.application.port.out.CatalogClient;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.http.RestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient restClient;

    public HttpCatalogClient(RestClientFactory restClients, @Value("${clients.catalog.url}") String baseUrl) {
        this.restClient = restClients.forBaseUrl(baseUrl);
    }

    @Override
    public SkuResponse getSku(String skuId) {
        return restClient.get()
                .uri("/internal/skus/{skuId}", skuId)
                .retrieve()
                .body(SkuResponse.class);
    }
}
