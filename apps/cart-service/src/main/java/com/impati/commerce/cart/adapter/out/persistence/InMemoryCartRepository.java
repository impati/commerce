package com.impati.commerce.cart.adapter.out.persistence;

import com.impati.commerce.cart.application.CartRepository;
import com.impati.commerce.cart.domain.CartModels.Cart;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCartRepository implements CartRepository {
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findByMemberId(String memberId) {
        return Optional.ofNullable(carts.get(memberId));
    }

    @Override
    public void save(Cart cart) {
        carts.put(cart.memberId(), cart);
    }
}
