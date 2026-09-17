package com.impati.commerce.storefront.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.RestClientFactory;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.storefront.application.port.out.CatalogClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;
    public HttpCatalogClient(RestClientFactory factory, ServiceCallExecutor calls, @Value("${clients.catalog.url}") String url) {
        this.restClient = factory.forBaseUrl(url);
        this.calls = calls;
    }
    public SkuResponse sku(String id) {
        return calls.query("storefront sku lookup", () -> restClient.get().uri("/internal/skus/{id}", id).retrieve().body(SkuResponse.class));
    }
    public ProductResponse product(String id) {
        return calls.query("storefront product lookup", () -> restClient.get().uri("/products/{id}", id).retrieve().body(ProductResponse.class));
    }
}
