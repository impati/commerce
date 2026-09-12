package com.impati.commerce.cart.application.component;

import com.impati.commerce.cart.application.port.in.CartUseCase;
import com.impati.commerce.cart.application.port.out.CartRepository;
import com.impati.commerce.cart.application.port.out.CatalogClient;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import org.junit.jupiter.api.BeforeEach;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@RequiresDatabase
class CartExecutorTest {

    private static final String SKU_ID = "sku_tee_white_m";

    @Autowired
    private CartUseCase cartUseCase;

    @Autowired
    private CartRepository cartRepository;

    @MockBean
    private CatalogClient catalogClient;

    @BeforeEach
    void stubCatalog() {
        when(catalogClient.getSku(anyString())).thenReturn(new SkuResponse(
                SKU_ID,
                "prd_tee",
                "White / M",
                Money.krw(29_000),
                Map.of(),
                "ON_SALE"
        ));
    }

    /** [PD-0018-R1] 같은 상품을 다시 담으면 줄이 늘지 않고 수량이 합산된다. */
    @Test
    void addItemAccumulatesQuantityForSameSku() {
        var memberId = "mem_accumulate";

        cartUseCase.addItem(memberId, SKU_ID, 2);
        var cart = cartUseCase.addItem(memberId, SKU_ID, 3);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().getFirst().skuId()).isEqualTo(SKU_ID);
        assertThat(cart.lines().getFirst().quantity()).isEqualTo(5);
    }

    /**
     * [PD-0018-R5] 조회는 저장소를 바꾸지 않는다. 이전 구현은 computeIfAbsent라서 조회만으로 장바구니가 생겼다.
     * DB에서는 GET에 INSERT가 따라붙는 셈이 되므로 여기서 막는다. problem/001 참고.
     */
    @Test
    void getDoesNotCreateCart() {
        var response = cartUseCase.get("mem_never_seen");

        assertThat(response.memberId()).isEqualTo("mem_never_seen");
        assertThat(response.lines()).isEmpty();
        assertThat(cartRepository.findByMemberId("mem_never_seen")).isEmpty();
    }

    /** [PD-0018-R8] 명시적으로 비운 장바구니는 저장된 채 비어 있다. 없는 장바구니와 다르다. */
    @Test
    void clearEmptiesStoredCart() {
        var memberId = "mem_clear";
        cartUseCase.addItem(memberId, SKU_ID, 2);

        var cleared = cartUseCase.clear(memberId);

        assertThat(cleared.lines()).isEmpty();
        assertThat(cartRepository.findByMemberId(memberId)).isPresent();
        assertThat(cartRepository.findByMemberId(memberId).orElseThrow().lines()).isEmpty();
    }
}
