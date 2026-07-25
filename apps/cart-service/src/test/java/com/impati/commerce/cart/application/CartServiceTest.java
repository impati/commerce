package com.impati.commerce.cart.application;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cart-app;DB_CLOSE_DELAY=-1")
class CartServiceTest {
    private static final String SKU_ID = "sku_tee_white_m";

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository carts;

    @MockBean
    private CatalogClient catalog;

    @BeforeEach
    void stubCatalog() {
        when(catalog.getSku(anyString())).thenReturn(new SkuResponse(
                SKU_ID,
                "prd_tee",
                "White / M",
                Money.krw(29_000),
                Map.of(),
                "ON_SALE"
        ));
    }

    @Test
    void addItemAccumulatesQuantityForSameSku() {
        var memberId = "mem_accumulate";

        cartService.addItem(memberId, SKU_ID, 2);
        var cart = cartService.addItem(memberId, SKU_ID, 3);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().getFirst().skuId()).isEqualTo(SKU_ID);
        assertThat(cart.lines().getFirst().quantity()).isEqualTo(5);
    }

    /**
     * 조회는 저장소를 바꾸지 않는다. 이전 구현은 computeIfAbsent라서 조회만으로 장바구니가 생겼다.
     * DB에서는 GET에 INSERT가 따라붙는 셈이 되므로 여기서 막는다. problem/001 참고.
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
        var memberId = "mem_clear";
        cartService.addItem(memberId, SKU_ID, 2);

        var cleared = cartService.clear(memberId);

        assertThat(cleared.lines()).isEmpty();
        assertThat(carts.findByMemberId(memberId)).isPresent();
        assertThat(carts.findByMemberId(memberId).orElseThrow().lines()).isEmpty();
    }
}
