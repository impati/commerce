package com.impati.commerce.cart.adapter.in.web;

import com.impati.commerce.cart.application.CartService;
import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/carts")
public class CartController {
    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping("/{memberId}")
    CartResponse get(@PathVariable String memberId) {
        return carts.get(memberId);
    }

    @PostMapping("/{memberId}/items")
    CartResponse addItem(@PathVariable String memberId, @RequestBody CartItemRequest request) {
        return carts.addItem(memberId, request.skuId(), request.quantity());
    }

    @PostMapping("/{memberId}/clear")
    CartResponse clear(@PathVariable String memberId) {
        return carts.clear(memberId);
    }
}

