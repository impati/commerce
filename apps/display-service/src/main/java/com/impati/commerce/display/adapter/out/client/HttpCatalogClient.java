package com.impati.commerce.display.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.display.application.CatalogClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient restClient;

    public HttpCatalogClient(RestClient.Builder builder, @Value("${clients.catalog.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public List<ProductResponse> products() {
        return restClient.get()
                .uri("/products")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
