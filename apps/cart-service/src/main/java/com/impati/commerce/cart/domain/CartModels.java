package com.impati.commerce.cart.domain;

import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.DomainException;

import java.util.ArrayList;
import java.util.List;

public final class CartModels {
    private CartModels() {
    }

    public static final class CartLine {
        private final String skuId;
        private int quantity;

        CartLine(String skuId, int quantity) {
            this.skuId = skuId;
            changeQuantity(quantity);
        }

        public String skuId() {
            return skuId;
        }

        public int quantity() {
            return quantity;
        }

        public void changeQuantity(int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("cart quantity must be positive");
            }
            this.quantity = quantity;
        }

        public CartLineResponse toResponse() {
            return new CartLineResponse(skuId, quantity);
        }
    }

    public static final class Cart {
        private final String memberId;
        private final List<CartLine> lines = new ArrayList<>();

        public Cart(String memberId) {
            this.memberId = memberId;
        }

        public String memberId() {
            return memberId;
        }

        public void add(String skuId, int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("cart quantity must be positive");
            }
            for (var line : lines) {
                if (line.skuId().equals(skuId)) {
                    line.changeQuantity(line.quantity() + quantity);
                    return;
                }
            }
            lines.add(new CartLine(skuId, quantity));
        }

        public void clear() {
            lines.clear();
        }

        public CartResponse toResponse() {
            return new CartResponse(memberId, lines.stream().map(CartLine::toResponse).toList());
        }
    }
}

