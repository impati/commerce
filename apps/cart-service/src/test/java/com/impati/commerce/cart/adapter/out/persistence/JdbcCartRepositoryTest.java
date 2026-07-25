package com.impati.commerce.cart.adapter.out.persistence;

import com.impati.commerce.cart.application.CartRepository;
import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.cart.domain.CartModels.CartLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cart-repo;DB_CLOSE_DELAY=-1")
class JdbcCartRepositoryTest {
    @Autowired
    private CartRepository carts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsLinesInInsertionOrder() {
        var cart = new Cart("mem_round");
        cart.add("sku_a", 1);
        cart.add("sku_b", 2);
        cart.add("sku_c", 3);

        carts.save(cart);
        var loaded = carts.findByMemberId("mem_round").orElseThrow();

        assertThat(loaded.lines().stream().map(CartLine::skuId)).containsExactly("sku_a", "sku_b", "sku_c");
        assertThat(loaded.lines().stream().map(CartLine::quantity)).containsExactly(1, 2, 3);
    }

    @Test
    void savingAgainReplacesLines() {
        var cart = new Cart("mem_replace");
        cart.add("sku_a", 1);
        carts.save(cart);

        cart.clear();
        cart.add("sku_z", 9);
        carts.save(cart);

        var loaded = carts.findByMemberId("mem_replace").orElseThrow();
        assertThat(loaded.lines()).hasSize(1);
        assertThat(loaded.lines().getFirst().skuId()).isEqualTo("sku_z");
        assertThat(loaded.lines().getFirst().quantity()).isEqualTo(9);
    }

    /** 빈 장바구니와 없는 장바구니는 다르다. clear 후에는 행이 남아 있어야 한다. */
    @Test
    void distinguishesEmptyCartFromMissingCart() {
        var cart = new Cart("mem_empty");
        carts.save(cart);

        assertThat(carts.findByMemberId("mem_empty")).isPresent();
        assertThat(carts.findByMemberId("mem_empty").orElseThrow().lines()).isEmpty();
        assertThat(carts.findByMemberId("mem_missing")).isEmpty();
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachLineFieldToItsOwnColumn() {
        var cart = new Cart("mem_column");
        cart.add("sku_first", 4);
        cart.add("sku_second", 7);

        carts.save(cart);

        var second = jdbc.queryForMap(
                "select sku_id, line_no, quantity from cart_lines where member_id = ? and line_no = 1",
                "mem_column"
        );
        assertThat(second.get("SKU_ID")).isEqualTo("sku_second");
        assertThat(second.get("LINE_NO")).isEqualTo(1);
        assertThat(second.get("QUANTITY")).isEqualTo(7);
    }
}
