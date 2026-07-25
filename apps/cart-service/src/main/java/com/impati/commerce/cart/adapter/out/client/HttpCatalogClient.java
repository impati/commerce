package com.impati.commerce.cart.adapter.out.client;

import com.impati.commerce.cart.application.CatalogClient;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient restClient;

    public HttpCatalogClient(RestClient.Builder builder, @Value("${clients.catalog.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public SkuResponse getSku(String skuId) {
        return restClient.get()
                .uri("/skus/{skuId}", skuId)
                .retrieve()
                .body(SkuResponse.class);
    }
}
