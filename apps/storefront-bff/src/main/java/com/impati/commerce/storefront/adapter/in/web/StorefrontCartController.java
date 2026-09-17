package com.impati.commerce.storefront.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.common.ApiContracts.StorefrontCartResponse;
import com.impati.commerce.storefront.application.port.in.CartCommandUseCase;
import com.impati.commerce.storefront.application.port.in.CartPageUseCase;
import com.impati.commerce.storefront.application.port.in.PurchaseUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorefrontCartController {
    private final CartPageUseCase cartPageUseCase;
    private final CartCommandUseCase cartCommandUseCase;
    private final PurchaseUseCase purchaseUseCase;

    public StorefrontCartController(
            CartPageUseCase cartPageUseCase,
            CartCommandUseCase cartCommandUseCase,
            PurchaseUseCase purchaseUseCase
    ) {
        this.cartPageUseCase = cartPageUseCase;
        this.cartCommandUseCase = cartCommandUseCase;
        this.purchaseUseCase = purchaseUseCase;
    }

    @GetMapping("/cart")
    StorefrontCartResponse get(@RequestHeader("X-Member-Id") String memberId) {
        return CartPageResponseMapper.from(cartPageUseCase.get(memberId));
    }

    @PostMapping("/cart/items")
    CartResponse add(
            @RequestHeader("X-Member-Id") String memberId,
            @RequestBody CartItemRequest request
    ) {
        var cart = cartCommandUseCase.addItem(memberId, request.skuId(), request.quantity());
        return new CartResponse(cart.memberId(), cart.lines(), cart.version());
    }

    @PostMapping("/checkout")
    ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader("X-Member-Id") String memberId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody ConfirmedCheckoutRequest request
    ) {
        var result = purchaseUseCase.checkout(
                memberId, key, request.paymentToken(), request.addressId(), request.quoteId());
        var response = new CheckoutResponse(result.order(), result.payment(), result.shipment());
        return switch (result.acceptance()) {
            case PROCESSING -> ResponseEntity.accepted().body(response);
            case NEWLY_ACCEPTED -> ResponseEntity.status(201).body(response);
            case REPLAYED -> ResponseEntity.ok(response);
        };
    }
}
