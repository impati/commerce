package com.impati.commerce.cart.application;

import com.impati.commerce.cart.domain.CartModels.Cart;

import java.util.Optional;

/**
 * 장바구니 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>조회는 조회만 한다. 없는 장바구니를 만들어 넣지 않는다 — 생성은 애플리케이션의 책임이다.
 */
public interface CartRepository {
    Optional<Cart> findByMemberId(String memberId);

    void save(Cart cart);
}
