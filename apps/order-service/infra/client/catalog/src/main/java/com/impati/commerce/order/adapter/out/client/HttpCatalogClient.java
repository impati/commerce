package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.DownstreamError;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.order.application.port.out.CatalogClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpCatalogClient(RestClient catalogRestClient, ServiceCallExecutor calls) {
        this.restClient = catalogRestClient;
        this.calls = calls;
    }

    @Override
    public SkuResponse sku(String skuId) {
        return calls.query("checkout sku lookup", () -> restClient.get()
                .uri("/internal/skus/{skuId}", skuId)
                .retrieve()
                .body(SkuResponse.class), HttpCatalogClient::cartChangedWhenMissing);
    }

    @Override
    public ProductResponse product(String productId) {
        return calls.query("checkout product lookup", () -> restClient.get()
                .uri("/products/{productId}", productId)
                .retrieve()
                .body(ProductResponse.class), HttpCatalogClient::cartChangedWhenMissing);
    }

    private static void cartChangedWhenMissing(DownstreamError error) {
        if (error.hasCode("not_found")) {
            throw DomainException.cartChanged("cart contains a product that is no longer available");
        }
    }
}
