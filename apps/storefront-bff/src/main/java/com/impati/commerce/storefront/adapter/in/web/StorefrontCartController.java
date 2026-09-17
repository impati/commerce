package com.impati.commerce.storefront.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.common.ApiContracts.StorefrontCartResponse;
import com.impati.commerce.storefront.application.port.in.CartPageUseCase;
import com.impati.commerce.storefront.application.port.out.CartClient;
import com.impati.commerce.storefront.application.port.out.OrderClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorefrontCartController {

    private final CartPageUseCase cartPageUseCase;
    private final CartClient cartClient;
    private final OrderClient orderClient;

    public StorefrontCartController(CartPageUseCase cartPageUseCase, CartClient cartClient, OrderClient orderClient) {
        this.cartPageUseCase = cartPageUseCase;
        this.cartClient = cartClient;
        this.orderClient = orderClient;
    }

    @GetMapping("/cart")
    StorefrontCartResponse get(@RequestHeader("X-Member-Id") String memberId) {
        return CartPageResponseMapper.from(cartPageUseCase.get(memberId));
    }

    @PostMapping("/cart/items")
    CartResponse add(@RequestHeader("X-Member-Id") String memberId, @RequestBody CartItemRequest request) {
        return cartClient.add(memberId, request);
    }

    @PostMapping("/checkout")
    ResponseEntity<CheckoutResponse> checkout(@RequestHeader("X-Member-Id") String memberId,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody ConfirmedCheckoutRequest request
    ) {
        return orderClient.checkout(memberId, key, request);
    }
}
