package com.impati.commerce.cart.adapter.in.web;

import com.impati.commerce.cart.application.port.in.CartUseCase;
import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.ChangeCartQuantityRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final CartUseCase cartUseCase;

    public CartController(CartUseCase cartUseCase) {
        this.cartUseCase = cartUseCase;
    }

    @GetMapping
    CartResponse get(@RequestHeader("X-Member-Id") String memberId) {
        return CartResponseMapper.from(cartUseCase.get(memberId));
    }

    @PostMapping("/items")
    CartResponse addItem(@RequestHeader("X-Member-Id") String memberId, @RequestBody CartItemRequest request) {
        return CartResponseMapper.from(cartUseCase.addItem(memberId, request.skuId(), request.quantity()));
    }

    @PutMapping("/items/{skuId}")
    CartResponse changeQuantity(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable String skuId,
            @RequestBody ChangeCartQuantityRequest request
    ) {
        return CartResponseMapper.from(cartUseCase.changeQuantity(
                memberId, skuId, request.quantityAsInt(), request.expectedVersionAsLong()));
    }

    @DeleteMapping("/items/{skuId}")
    CartResponse remove(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable String skuId,
            @RequestParam long expectedVersion
    ) {
        return CartResponseMapper.from(cartUseCase.removeItem(memberId, skuId, expectedVersion));
    }
}
