package com.impati.commerce.display.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.display.application.port.out.CatalogClient;
import com.impati.commerce.http.RestClientFactory;
import com.impati.commerce.http.ServiceCallExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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
    public List<ProductResponse> products() {
        return calls.query("catalog products lookup", () -> restClient.get()
                .uri("/products")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                }));
    }
}
