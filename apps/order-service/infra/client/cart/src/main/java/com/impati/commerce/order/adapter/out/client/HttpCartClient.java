package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutCartRequest;
import com.impati.commerce.common.ApiContracts.CheckoutCartResponse;
import com.impati.commerce.order.application.port.out.CartClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import com.impati.commerce.common.DomainException;

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

    @Override
    public CheckoutCartResponse checkout(String memberId, String orderId, long expectedVersion) {
        try {
            return restClient.post()
                    .uri("/internal/carts/checkout")
                    .header(MEMBER_ID_HEADER, memberId)
                    .body(new CheckoutCartRequest(orderId, expectedVersion))
                    .retrieve()
                    .body(CheckoutCartResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getResponseBodyAsString().contains("cart_empty")) {
                throw DomainException.cartEmpty("cart is empty");
            }
            if (exception.getResponseBodyAsString().contains("cart_changed")) {
                throw DomainException.cartChanged("cart changed after checkout started");
            }
            throw DomainException.unavailable("cart service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.outcomeUnknown("cart checkout outcome unknown: " + exception.getMessage());
        }
    }
}
