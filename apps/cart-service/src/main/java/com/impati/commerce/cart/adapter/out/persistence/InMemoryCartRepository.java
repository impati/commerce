package com.impati.commerce.cart.adapter.out.persistence;

import com.impati.commerce.cart.domain.CartModels.Cart;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCartRepository {
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();

    public Cart getOrCreate(String memberId) {
        return carts.computeIfAbsent(memberId, Cart::new);
    }

    public void save(Cart cart) {
        carts.put(cart.memberId(), cart);
    }
}

