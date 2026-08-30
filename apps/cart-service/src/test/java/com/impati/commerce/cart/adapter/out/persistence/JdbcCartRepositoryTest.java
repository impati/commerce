package com.impati.commerce.cart.adapter.out.persistence;

import com.impati.commerce.cart.application.port.out.CartRepository;
import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.cart.domain.CartModels.CartLine;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.TestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresDatabase
class JdbcCartRepositoryTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry, "cart-repo");
    }
    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsLinesInInsertionOrder() {
        var cart = new Cart("mem_round");
        cart.add("sku_a", 1);
        cart.add("sku_b", 2);
        cart.add("sku_c", 3);

        cartRepository.save(cart);
        var loaded = cartRepository.findByMemberId("mem_round").orElseThrow();

        assertThat(loaded.lines().stream().map(CartLine::skuId)).containsExactly("sku_a", "sku_b", "sku_c");
        assertThat(loaded.lines().stream().map(CartLine::quantity)).containsExactly(1, 2, 3);
    }

    @Test
    void savingAgainReplacesLines() {
        var cart = new Cart("mem_replace");
        cart.add("sku_a", 1);
        cartRepository.save(cart);

        cart.clear();
        cart.add("sku_z", 9);
        cartRepository.save(cart);

        var loaded = cartRepository.findByMemberId("mem_replace").orElseThrow();
        assertThat(loaded.lines()).hasSize(1);
        assertThat(loaded.lines().getFirst().skuId()).isEqualTo("sku_z");
        assertThat(loaded.lines().getFirst().quantity()).isEqualTo(9);
    }

    /** [PD-0006-R7] 빈 장바구니와 없는 장바구니는 다르다. clear 후에는 행이 남아 있어야 한다. */
    @Test
    void distinguishesEmptyCartFromMissingCart() {
        var cart = new Cart("mem_empty");
        cartRepository.save(cart);

        assertThat(cartRepository.findByMemberId("mem_empty")).isPresent();
        assertThat(cartRepository.findByMemberId("mem_empty").orElseThrow().lines()).isEmpty();
        assertThat(cartRepository.findByMemberId("mem_missing")).isEmpty();
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachLineFieldToItsOwnColumn() {
        var cart = new Cart("mem_column");
        cart.add("sku_first", 4);
        cart.add("sku_second", 7);

        cartRepository.save(cart);

        var second = jdbc.queryForMap(
                "select sku_id, line_no, quantity from cart_lines where member_id = ? and line_no = 1",
                "mem_column"
        );
        assertThat(second.get("sku_id")).isEqualTo("sku_second");
        assertThat(second.get("line_no")).isEqualTo(1);
        assertThat(second.get("quantity")).isEqualTo(7);
    }
}
