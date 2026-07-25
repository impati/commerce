package com.impati.commerce.cart.application;

import com.impati.commerce.cart.domain.CartModels.Cart;

/**
 * 장바구니 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface CartRepository {
    /**
     * 없으면 빈 장바구니를 만들어 돌려준다. 조회만으로 행이 생기는 셈이라
     * DB 구현에서는 findById + 애플리케이션 기본값으로 나눠야 한다.
     */
    Cart getOrCreate(String memberId);

    void save(Cart cart);
}
