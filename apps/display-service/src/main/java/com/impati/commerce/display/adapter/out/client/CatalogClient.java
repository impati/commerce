package com.impati.commerce.display.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class CatalogClient {
    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder, @Value("${clients.catalog.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<ProductResponse> products() {
        return restClient.get()
                .uri("/products")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}

