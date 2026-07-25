package com.impati.commerce.cart.application;

import com.impati.commerce.cart.adapter.out.client.CatalogClient;
import com.impati.commerce.cart.adapter.out.persistence.InMemoryCartRepository;
import com.impati.commerce.common.ApiContracts.CartResponse;
import org.springframework.stereotype.Service;

@Service
public class CartService {
    private final InMemoryCartRepository carts;
    private final CatalogClient catalog;

    public CartService(InMemoryCartRepository carts, CatalogClient catalog) {
        this.carts = carts;
        this.catalog = catalog;
    }

    public CartResponse addItem(String memberId, String skuId, int quantity) {
        catalog.getSku(skuId);
        var cart = carts.getOrCreate(memberId);
        cart.add(skuId, quantity);
        carts.save(cart);
        return cart.toResponse();
    }

    public CartResponse get(String memberId) {
        return carts.getOrCreate(memberId).toResponse();
    }

    public CartResponse clear(String memberId) {
        var cart = carts.getOrCreate(memberId);
        cart.clear();
        carts.save(cart);
        return cart.toResponse();
    }
}

