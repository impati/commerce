package com.impati.commerce.cart.adapter.in.web;

import com.impati.commerce.cart.application.CartService;
import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니 API.
 *
 * <p>경로에 memberId를 두지 않는다. 게이트웨이가 세션을 검증해 {@code X-Member-Id}로 신원을
 * 넘긴다. 경로로 받으면 남의 장바구니를 조회하는 것을 서비스가 따로 막아야 한다.
 */
@RestController
@RequestMapping("/carts")
public class CartController {
    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    CartResponse get(@RequestHeader("X-Member-Id") String memberId) {
        return carts.get(memberId);
    }

    @PostMapping("/items")
    CartResponse addItem(@RequestHeader("X-Member-Id") String memberId, @RequestBody CartItemRequest request) {
        return carts.addItem(memberId, request.skuId(), request.quantity());
    }

    @PostMapping("/clear")
    CartResponse clear(@RequestHeader("X-Member-Id") String memberId) {
        return carts.clear(memberId);
    }
}
