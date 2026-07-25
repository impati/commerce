package com.impati.commerce.cart.application;

import com.impati.commerce.cart.adapter.out.client.CatalogClient;
import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.common.ApiContracts.CartResponse;
import org.springframework.stereotype.Service;

@Service
public class CartService {
    private final CartRepository carts;
    private final CatalogClient catalog;

    public CartService(CartRepository carts, CatalogClient catalog) {
        this.carts = carts;
        this.catalog = catalog;
    }

    public CartResponse addItem(String memberId, String skuId, int quantity) {
        catalog.getSku(skuId);
        var cart = loadOrNew(memberId);
        cart.add(skuId, quantity);
        carts.save(cart);
        return CartMapper.toResponse(cart);
    }

    /** 조회는 저장소를 바꾸지 않는다. 장바구니가 없으면 빈 것을 만들어 응답만 하고 저장하지 않는다. */
    public CartResponse get(String memberId) {
        return CartMapper.toResponse(loadOrNew(memberId));
    }

    public CartResponse clear(String memberId) {
        var cart = loadOrNew(memberId);
        cart.clear();
        carts.save(cart);
        return CartMapper.toResponse(cart);
    }

    private Cart loadOrNew(String memberId) {
        return carts.findByMemberId(memberId).orElseGet(() -> new Cart(memberId));
    }
}
