package com.impati.commerce.cart;

import com.impati.commerce.cart.application.port.out.CartRepository;
import com.impati.commerce.cart.application.port.out.CatalogClient;
import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.test.RequiresDatabase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@RequiresDatabase
class CartLineManagementTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private CartRepository carts;

    @MockBean
    private CatalogClient catalogClient;

    @Test
    void changesTheAbsoluteQuantityAndRemovesOnlyTheSelectedLine() throws Exception {
        var cart = savedCart();
        mvc.perform(put("/carts/items/sku_a")
                        .header("X-Member-Id", cart.memberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(7, cart.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(cart.version() + 1))
                .andExpect(jsonPath("$.lines[0].quantity").value(7))
                .andExpect(jsonPath("$.lines[1].quantity").value(3));
        var updated = carts.findByMemberId(cart.memberId()).orElseThrow();
        assertThat(updated.lines().getFirst().quantity()).isEqualTo(7);
        mvc.perform(delete("/carts/items/sku_a")
                        .header("X-Member-Id", cart.memberId())
                        .param("expectedVersion", String.valueOf(updated.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(updated.version() + 1))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].skuId").value("sku_b"));
        assertThat(carts.findByMemberId(cart.memberId()).orElseThrow().lines())
                .extracting(line -> line.skuId()).containsExactly("sku_b");
        // Existing cart intent remains editable even when the catalog cannot be queried.
        verifyNoInteractions(catalogClient);
    }

    @Test
    void identicalQuantityPreservesTheVersionButStillRejectsAStaleVersion() throws Exception {
        var cart = savedCart();
        mvc.perform(put("/carts/items/sku_a")
                        .header("X-Member-Id", cart.memberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(2, cart.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(cart.version()));
        mvc.perform(put("/carts/items/sku_a")
                        .header("X-Member-Id", cart.memberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(2, cart.version() - 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cart_changed"));
        assertThat(carts.findByMemberId(cart.memberId()).orElseThrow().version()).isEqualTo(cart.version());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantitiesWithoutSaving(int quantity) throws Exception {
        var cart = savedCart();
        mvc.perform(put("/carts/items/sku_a")
                        .header("X-Member-Id", cart.memberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(quantity, cart.version())))
                .andExpect(status().isBadRequest());
        assertThat(carts.findByMemberId(cart.memberId()).orElseThrow().lines().getFirst().quantity()).isEqualTo(2);
        assertThat(carts.findByMemberId(cart.memberId()).orElseThrow().version()).isEqualTo(cart.version());
    }

    @Test
    void neitherAnOldNorAFutureVersionCanModifyOrDeleteLines() throws Exception {
        var cart = savedCart();
        for (var version : new long[] {cart.version() - 1, cart.version() + 1}) {
            mvc.perform(put("/carts/items/sku_a")
                            .header("X-Member-Id", cart.memberId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request(7, version)))
                    .andExpect(status().isConflict());
            mvc.perform(delete("/carts/items/sku_a")
                            .header("X-Member-Id", cart.memberId())
                            .param("expectedVersion", String.valueOf(version)))
                    .andExpect(status().isConflict());
        }
        assertThat(carts.findByMemberId(cart.memberId()).orElseThrow().version()).isEqualTo(cart.version());
        assertThat(carts.findByMemberId(cart.memberId()).orElseThrow().lines()).hasSize(2);
    }

    @Test
    void aMissingLineIsRejectedAndAnAbsentCartIsNotCreated() throws Exception {
        var cart = savedCart();
        mvc.perform(put("/carts/items/missing")
                        .header("X-Member-Id", cart.memberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(7, cart.version())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/carts/items/missing")
                        .header("X-Member-Id", cart.memberId())
                        .param("expectedVersion", String.valueOf(cart.version())))
                .andExpect(status().isNotFound());
        var absentMember = UUID.randomUUID().toString();
        mvc.perform(delete("/carts/items/sku_a")
                        .header("X-Member-Id", absentMember)
                        .param("expectedVersion", "0"))
                .andExpect(status().isNotFound());
        assertThat(carts.findByMemberId(absentMember)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"quantity\":1.5,\"expectedVersion\":2}",
            "{\"quantity\":2,\"expectedVersion\":2.5}",
            "{\"quantity\":2147483648,\"expectedVersion\":2}",
            "{\"quantity\":2,\"expectedVersion\":9223372036854775808}",
            "{\"quantity\":null,\"expectedVersion\":2}",
            "{\"quantity\":2}"
    })
    void rejectsMalformedNumbersInsteadOfTruncatingOrDefaultingThem(String request) throws Exception {
        var cart = savedCart();
        mvc.perform(put("/carts/items/sku_a")
                        .header("X-Member-Id", cart.memberId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        var stored = carts.findByMemberId(cart.memberId()).orElseThrow();
        assertThat(stored.version()).isEqualTo(cart.version());
        assertThat(stored.lines().getFirst().quantity()).isEqualTo(2);
    }

    private Cart savedCart() {
        var cart = new Cart(UUID.randomUUID().toString());
        cart.add("sku_a", 2);
        cart.add("sku_b", 3);
        carts.save(cart);
        return cart;
    }

    private String request(int quantity, long version) {
        return "{\"quantity\":%d,\"expectedVersion\":%d}".formatted(quantity, version);
    }
}
