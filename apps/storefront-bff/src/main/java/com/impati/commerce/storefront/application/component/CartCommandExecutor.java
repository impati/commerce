package com.impati.commerce.storefront.application.component;

import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.ChangeCartQuantityRequest;
import com.impati.commerce.storefront.application.model.CartState;
import com.impati.commerce.storefront.application.port.in.CartCommandUseCase;
import com.impati.commerce.storefront.application.port.out.CartClient;
import org.springframework.stereotype.Component;

@Component
public class CartCommandExecutor implements CartCommandUseCase {
    private final CartClient cartClient;

    public CartCommandExecutor(CartClient cartClient) {
        this.cartClient = cartClient;
    }

    @Override
    public CartState addItem(String memberId, String skuId, int quantity) {
        var cart = cartClient.add(memberId, new CartItemRequest(skuId, quantity));
        return new CartState(cart.memberId(), cart.version(), cart.lines());
    }

    @Override
    public CartState changeQuantity(String memberId, String skuId, int quantity, long expectedVersion) {
        var cart = cartClient.changeQuantity(memberId, skuId, new ChangeCartQuantityRequest(quantity, expectedVersion));
        return new CartState(cart.memberId(), cart.version(), cart.lines());
    }

    @Override
    public CartState removeItem(String memberId, String skuId, long expectedVersion) {
        var cart = cartClient.remove(memberId, skuId, expectedVersion);
        return new CartState(cart.memberId(), cart.version(), cart.lines());
    }
}
