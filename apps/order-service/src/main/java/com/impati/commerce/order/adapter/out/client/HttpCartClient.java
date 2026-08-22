package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.order.application.port.out.CartClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCartClient implements CartClient {
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final RestClient restClient;

    public HttpCartClient(RestClient cartRestClient) {
        this.restClient = cartRestClient;
    }

    @Override
    public CartResponse cart(String memberId) {
        return restClient.get()
                .uri("/carts")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(CartResponse.class);
    }

    @Override
    public void clearCart(String memberId) {
        restClient.post()
                .uri("/internal/carts/clear")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .toBodilessEntity();
    }
}
