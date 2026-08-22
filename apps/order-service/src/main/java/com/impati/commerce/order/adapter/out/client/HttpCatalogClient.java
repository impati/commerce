package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.order.application.port.out.CatalogClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient restClient;

    public HttpCatalogClient(RestClient catalogRestClient) {
        this.restClient = catalogRestClient;
    }

    @Override
    public SkuResponse sku(String skuId) {
        return restClient.get().uri("/internal/skus/{skuId}", skuId).retrieve().body(SkuResponse.class);
    }

    @Override
    public ProductResponse product(String productId) {
        return restClient.get().uri("/products/{productId}", productId).retrieve().body(ProductResponse.class);
    }
}
