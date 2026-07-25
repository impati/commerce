package com.impati.commerce.cart.application;

import com.impati.commerce.cart.adapter.out.client.CatalogClient;
import com.impati.commerce.cart.adapter.out.persistence.InMemoryCartRepository;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CartServiceTest {
    private static final String MEMBER_ID = "mem_demo";
    private static final String SKU_ID = "sku_tee_white_m";

    private InMemoryCartRepository carts;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        var catalog = mock(CatalogClient.class);
        when(catalog.getSku(anyString())).thenReturn(new SkuResponse(
                SKU_ID,
                "prd_tee",
                "White / M",
                Money.krw(29_000),
                Map.of(),
                "ON_SALE"
        ));
        carts = new InMemoryCartRepository();
        cartService = new CartService(carts, catalog);
    }

    @Test
    void addItemAccumulatesQuantityForSameSku() {
        cartService.addItem(MEMBER_ID, SKU_ID, 2);
        var cart = cartService.addItem(MEMBER_ID, SKU_ID, 3);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().getFirst().skuId()).isEqualTo(SKU_ID);
        assertThat(cart.lines().getFirst().quantity()).isEqualTo(5);
    }

    /**
     * 조회는 저장소를 바꾸지 않는다. 이전 구현은 computeIfAbsent라서 조회만으로 장바구니가 생겼다.
     * DB로 가면 GET에 INSERT가 따라붙는 셈이 되므로 여기서 막는다.
     */
    @Test
    void getDoesNotCreateCart() {
        var response = cartService.get("mem_never_seen");

        assertThat(response.memberId()).isEqualTo("mem_never_seen");
        assertThat(response.lines()).isEmpty();
        assertThat(carts.findByMemberId("mem_never_seen")).isEmpty();
    }

    @Test
    void clearEmptiesStoredCart() {
        cartService.addItem(MEMBER_ID, SKU_ID, 2);

        var cleared = cartService.clear(MEMBER_ID);

        assertThat(cleared.lines()).isEmpty();
        assertThat(carts.findByMemberId(MEMBER_ID)).isPresent();
        assertThat(carts.findByMemberId(MEMBER_ID).orElseThrow().toResponse().lines()).isEmpty();
    }
}
