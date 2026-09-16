package com.impati.commerce.cart.domain;

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
    }

    public static final class Cart {
        private final String memberId;
        private final List<CartLine> lines = new ArrayList<>();
        private long version;
        private long persistedVersion = -1;

        public Cart(String memberId) {
            this.memberId = memberId;
        }

        public static Cart restore(String memberId, long version) {
            var cart = new Cart(memberId);
            cart.version = version;
            cart.persistedVersion = version;
            return cart;
        }

        /** 영속화 어댑터가 저장된 라인을 복원할 때만 쓴다. 버전은 바꾸지 않는다. */
        public void restoreLine(String skuId, int quantity) {
            lines.add(new CartLine(skuId, quantity));
        }

        public String memberId() {
            return memberId;
        }

        public List<CartLine> lines() {
            return List.copyOf(lines);
        }

        public long version() {
            return version;
        }

        public long persistedVersion() { return persistedVersion; }

        public void markPersisted() { persistedVersion = version; }

        public void add(String skuId, int quantity) {
            if (quantity <= 0) {
                throw DomainException.validation("cart quantity must be positive");
            }
            for (var line : lines) {
                if (line.skuId().equals(skuId)) {
                    line.changeQuantity(line.quantity() + quantity);
                    version++;
                    return;
                }
            }
            lines.add(new CartLine(skuId, quantity));
            version++;
        }

        public void clear() {
            lines.clear();
            version++;
        }
    }
}
