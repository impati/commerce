package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutCartRequest;
import com.impati.commerce.common.ApiContracts.CheckoutCartResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.order.application.port.out.CartClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCartClient implements CartClient {
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpCartClient(RestClient cartRestClient, ServiceCallExecutor calls) {
        this.restClient = cartRestClient;
        this.calls = calls;
    }

    @Override
    public CartResponse cart(String memberId) {
        return calls.query("cart lookup", () -> restClient.get()
                .uri("/carts")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(CartResponse.class));
    }

    @Override
    public void clearCart(String memberId) {
        calls.command("cart clear", () -> restClient.post()
                .uri("/internal/carts/clear")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public CheckoutCartResponse checkout(String memberId, String orderId, long expectedVersion) {
        return calls.command("cart checkout", () -> restClient.post()
                    .uri("/internal/carts/checkout")
                    .header(MEMBER_ID_HEADER, memberId)
                    .body(new CheckoutCartRequest(orderId, expectedVersion))
                    .retrieve()
                    .body(CheckoutCartResponse.class), error -> {
            if (error.hasCode("cart_empty")) {
                throw DomainException.cartEmpty("cart is empty");
            }
            if (error.hasCode("cart_changed")) {
                throw DomainException.cartChanged("cart changed after checkout started");
            }
        });
    }
}
