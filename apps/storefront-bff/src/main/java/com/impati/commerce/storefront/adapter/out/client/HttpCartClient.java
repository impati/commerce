package com.impati.commerce.storefront.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.ChangeCartQuantityRequest;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.DownstreamError;
import com.impati.commerce.http.RestClientFactory;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.storefront.application.port.out.CartClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCartClient implements CartClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpCartClient(
            RestClientFactory factory,
            ServiceCallExecutor calls,
            @Value("${clients.cart.url}") String url
    ) {
        this.restClient = factory.forBaseUrl(url);
        this.calls = calls;
    }

    @Override
    public CartResponse get(String memberId) {
        return calls.query("storefront cart lookup", () -> restClient.get()
                .uri("/carts")
                .header("X-Member-Id", memberId)
                .retrieve()
                .body(CartResponse.class));
    }

    @Override
    public CartResponse add(String memberId, CartItemRequest request) {
        return calls.command("storefront cart add", () -> restClient.post()
                .uri("/carts/items")
                .header("X-Member-Id", memberId)
                .body(request)
                .retrieve()
                .body(CartResponse.class), error -> {
            if (error.hasCode("validation_error")) {
                throw DomainException.validation("수량을 확인해주세요.");
            }
            if (error.hasCode("not_found")) {
                throw DomainException.notFound("상품을 찾을 수 없습니다.");
            }
            if (error.hasCode("cart_changed")) {
                throw DomainException.cartChanged("장바구니가 변경됐습니다. 다시 확인해주세요.");
            }
        });
    }

    @Override
    public CartResponse changeQuantity(String memberId, String skuId, ChangeCartQuantityRequest request) {
        return calls.command("storefront cart quantity change", () -> restClient.put()
                .uri("/carts/items/{skuId}", skuId)
                .header("X-Member-Id", memberId)
                .body(request)
                .retrieve()
                .body(CartResponse.class), HttpCartClient::handleLineChangeError);
    }

    @Override
    public CartResponse remove(String memberId, String skuId, long expectedVersion) {
        return calls.command("storefront cart remove", () -> restClient.delete()
                .uri(builder -> builder.path("/carts/items/{skuId}")
                        .queryParam("expectedVersion", expectedVersion)
                        .build(skuId))
                .header("X-Member-Id", memberId)
                .retrieve()
                .body(CartResponse.class), HttpCartClient::handleLineChangeError);
    }

    private static void handleLineChangeError(DownstreamError error) {
        if (error.hasCode("validation_error")) {
            throw DomainException.validation("수량과 장바구니 버전을 확인해주세요.");
        }
        if (error.hasCode("not_found")) {
            throw DomainException.notFound("장바구니 상품을 찾을 수 없습니다. 다시 확인해주세요.");
        }
        if (error.hasCode("cart_changed")) {
            throw DomainException.cartChanged("장바구니가 변경됐습니다. 다시 확인해주세요.");
        }
    }
}
