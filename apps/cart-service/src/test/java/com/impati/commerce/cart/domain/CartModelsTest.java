package com.impati.commerce.cart.domain;

import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartModelsTest {
    @Test
    void anOldOrFutureVersionCannotChangeOrRemoveALine() {
        var cart = cart();
        for (var version : new long[] {cart.version() - 1, cart.version() + 1}) {
            assertThatThrownBy(() -> cart.changeQuantity("sku", 3, version))
                    .isInstanceOfSatisfying(DomainException.class,
                            failure -> assertThat(failure.code()).isEqualTo("cart_changed"));
            assertThatThrownBy(() -> cart.remove("sku", version))
                    .isInstanceOfSatisfying(DomainException.class,
                            failure -> assertThat(failure.code()).isEqualTo("cart_changed"));
        }
        assertThat(cart.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(cart.version()).isEqualTo(1);
    }

    @Test
    void anUnchangedQuantityDoesNotInvalidateTheQuote() {
        var cart = cart();
        cart.changeQuantity("sku", 2, cart.version());
        assertThat(cart.version()).isEqualTo(1);
        cart.changeQuantity("sku", 7, cart.version());
        assertThat(cart.lines().getFirst().quantity()).isEqualTo(7);
        assertThat(cart.version()).isEqualTo(2);
    }

    @Test
    void invalidQuantitiesAndMissingLinesNeverChangeTheCart() {
        var cart = cart();
        assertThatThrownBy(() -> cart.changeQuantity("sku", 0, cart.version())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> cart.changeQuantity("sku", -1, cart.version())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> cart.changeQuantity("missing", 3, cart.version())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> cart.remove("missing", cart.version())).isInstanceOf(DomainException.class);
        assertThat(cart.version()).isEqualTo(1);
        assertThat(cart.lines().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void removalIncrementsTheVersionAndCanLeaveAnEmptyCart() {
        var cart = cart();
        cart.remove("sku", cart.version());
        assertThat(cart.lines()).isEmpty();
        assertThat(cart.version()).isEqualTo(2);
    }

    private Cart cart() {
        var cart = new Cart("member");
        cart.add("sku", 2);
        return cart;
    }
}
