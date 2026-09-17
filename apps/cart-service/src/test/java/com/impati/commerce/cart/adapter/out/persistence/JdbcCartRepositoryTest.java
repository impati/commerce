package com.impati.commerce.cart.adapter.out.persistence;

import com.impati.commerce.cart.application.port.out.CartRepository;
import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.cart.domain.CartModels.CartLine;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.test.RequiresDatabase;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@RequiresDatabase
class JdbcCartRepositoryTest {

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

    /** [PD-0018-R8] 빈 장바구니와 없는 장바구니는 다르다. clear 후에는 행이 남아 있어야 한다. */
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

    @Test
    void checkoutDetachesOneSnapshotAndARepeatDoesNotRemoveNewItems() {
        var cart = new Cart("mem_checkout_snapshot");
        cart.add("sku_a", 1);
        cart.add("sku_b", 2);
        cartRepository.save(cart);

        var first = cartRepository.checkout("mem_checkout_snapshot", "ord_snapshot", cart.version());
        var afterDetach = cartRepository.findByMemberId("mem_checkout_snapshot").orElseThrow();
        afterDetach.add("sku_new", 3);
        cartRepository.save(afterDetach);

        var repeated = cartRepository.checkout("mem_checkout_snapshot", "ord_snapshot", cart.version());

        assertThat(first.lines()).extracting(CartLine::skuId).containsExactly("sku_a", "sku_b");
        assertThat(repeated.lines()).extracting(CartLine::skuId).containsExactly("sku_a", "sku_b");
        assertThat(cartRepository.findByMemberId("mem_checkout_snapshot").orElseThrow().lines())
                .extracting(CartLine::skuId)
                .containsExactly("sku_new");
    }

    @Test
    void checkoutRejectsAChangedCartWithoutRemovingItsLines() {
        var cart = new Cart("mem_checkout_changed");
        cart.add("sku_a", 1);
        cartRepository.save(cart);
        var staleVersion = cart.version();

        cart.add("sku_b", 2);
        cartRepository.save(cart);

        assertThatThrownBy(() -> cartRepository.checkout(
                "mem_checkout_changed", "ord_changed", staleVersion))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("cart_changed"));
        assertThat(cartRepository.findByMemberId("mem_checkout_changed").orElseThrow().lines())
                .extracting(CartLine::skuId)
                .containsExactly("sku_a", "sku_b");
    }

    /** [PD-0021-R3] 구매분 분리 이후 오래된 수정이 구매분을 다시 장바구니에 넣지 않는다. */
    @Test void staleSaveCannotRestoreDetachedPurchaseLines() {
        var cart = new Cart("mem_stale_after_checkout");
        cart.add("sku_a", 1);
        cartRepository.save(cart);
        var stale = cartRepository.findByMemberId(cart.memberId()).orElseThrow();
        cartRepository.checkout(cart.memberId(), "ord_stale_after_checkout", cart.version());
        stale.add("sku_b", 1);
        assertThatThrownBy(() -> cartRepository.save(stale)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.code()).isEqualTo("cart_changed"));
        assertThat(cartRepository.findByMemberId(cart.memberId()).orElseThrow().lines()).isEmpty();
    }

    @Test void concurrentVersionsCannotSilentlyOverwriteEachOther() {
        var cart = new Cart("mem_compare_and_save");
        cart.add("sku_a", 1);
        cartRepository.save(cart);
        var first = cartRepository.findByMemberId(cart.memberId()).orElseThrow();
        var second = cartRepository.findByMemberId(cart.memberId()).orElseThrow();
        first.add("sku_b", 2);
        second.add("sku_c", 3);
        cartRepository.save(first);
        assertThatThrownBy(() -> cartRepository.save(second)).isInstanceOf(DomainException.class);
        assertThat(cartRepository.findByMemberId(cart.memberId()).orElseThrow().lines())
                .extracting(CartLine::skuId).containsExactly("sku_a", "sku_b");
    }
    @Test
    void modificationAndCheckoutCannotBothCommitTheSameVersion() throws Exception {
        var cart = new Cart(UUID.randomUUID().toString());
        cart.add("sku_a", 2);
        cartRepository.save(cart);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var modification = executor.submit(() -> {
                var loaded = cartRepository.findByMemberId(cart.memberId()).orElseThrow();
                loaded.changeQuantity("sku_a", 7, cart.version());
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    cartRepository.save(loaded);
                    return true;
                } catch (DomainException failure) {
                    assertThat(failure.code()).isEqualTo("cart_changed");
                    return false;
                }
            });
            var checkout = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    cartRepository.checkout(cart.memberId(), UUID.randomUUID().toString(), cart.version());
                    return true;
                } catch (DomainException failure) {
                    assertThat(failure.code()).isEqualTo("cart_changed");
                    return false;
                }
            });
            var modified = modification.get(20, TimeUnit.SECONDS);
            var detached = checkout.get(20, TimeUnit.SECONDS);
            assertThat(modified).isNotEqualTo(detached);
            var stored = cartRepository.findByMemberId(cart.memberId()).orElseThrow();
            if (modified) {
                assertThat(stored.lines().getFirst().quantity()).isEqualTo(7);
            } else {
                assertThat(stored.lines()).isEmpty();
            }
            assertThat(stored.version()).isEqualTo(cart.version() + 1);
        }
    }
}
