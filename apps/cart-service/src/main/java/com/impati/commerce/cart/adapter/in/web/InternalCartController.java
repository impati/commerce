package com.impati.commerce.cart.adapter.in.web;

import com.impati.commerce.cart.application.CartService;
import com.impati.commerce.common.ApiContracts.CartResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게이트웨이가 노출하지 않는 경로. 형제 서비스만 부른다 (ADR-0003).
 *
 * <p>장바구니 비우기는 order-service가 checkout 뒤에 부른다. 사용자에게 열면 남의 요청 흐름과
 * 무관하게 장바구니를 비울 수 있고, 무엇보다 checkout의 일부이지 사용자가 직접 하는 동작이 아니다.
 */
@RestController
@RequestMapping("/internal/carts")
public class InternalCartController {
    private final CartService carts;

    public InternalCartController(CartService carts) {
        this.carts = carts;
    }

    @PostMapping("/clear")
    CartResponse clear(@RequestHeader("X-Member-Id") String memberId) {
        return carts.clear(memberId);
    }
}
