package com.impati.commerce.cart.application.component;

import com.impati.commerce.cart.application.port.in.CartDetails;
import com.impati.commerce.cart.application.port.in.CartUseCase;
import com.impati.commerce.cart.application.port.out.CartRepository;
import com.impati.commerce.cart.application.port.out.CatalogClient;
import com.impati.commerce.cart.domain.CartModels.Cart;
import org.springframework.stereotype.Component;

@Component
public class CartExecutor implements CartUseCase {
    private final CartRepository carts;
    private final CatalogClient catalog;

    public CartExecutor(CartRepository carts, CatalogClient catalog) {
        this.carts = carts;
        this.catalog = catalog;
    }

    @Override
    public CartDetails addItem(String memberId, String skuId, int quantity) {
        catalog.getSku(skuId);
        var cart = loadOrNew(memberId);
        cart.add(skuId, quantity);
        carts.save(cart);
        return CartMapper.toDetails(cart);
    }

    @Override
    public CartDetails get(String memberId) {
        return CartMapper.toDetails(loadOrNew(memberId));
    }

    @Override
    public CartDetails clear(String memberId) {
        var cart = loadOrNew(memberId);
        cart.clear();
        carts.save(cart);
        return CartMapper.toDetails(cart);
    }

    private Cart loadOrNew(String memberId) {
        return carts.findByMemberId(memberId).orElseGet(() -> new Cart(memberId));
    }
}
