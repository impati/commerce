package com.impati.commerce.storefront.application.component;

import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteLineResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.storefront.application.port.out.CartClient;
import com.impati.commerce.storefront.application.port.out.CatalogClient;
import com.impati.commerce.storefront.application.port.out.InventoryClient;
import com.impati.commerce.storefront.application.port.out.OrderClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CartPageExecutorTest {
    private final CartClient cartClient = mock(CartClient.class);
    private final CatalogClient catalogClient = mock(CatalogClient.class);
    private final InventoryClient inventoryClient = mock(InventoryClient.class);
    private final OrderClient orderClient = mock(OrderClient.class);
    private ExecutorService queryExecutor;
    private CartPageExecutor executor;

    @BeforeEach
    void setUp() {
        queryExecutor = Executors.newVirtualThreadPerTaskExecutor();
        executor = new CartPageExecutor(cartClient, catalogClient, inventoryClient, orderClient, queryExecutor);
        when(cartClient.get("m")).thenReturn(new CartResponse("m", List.of(new CartLineResponse("sku", 2)), 5));
        when(catalogClient.sku("sku")).thenReturn(new SkuResponse("sku", "p", "Sku", Money.krw(1), Map.of(), "PUBLISHED"));
        when(catalogClient.product("p")).thenReturn(new ProductResponse(
                "p", "Product", "B", "C", "D", "PUBLISHED", List.of(), List.of()));
        when(inventoryClient.get("sku")).thenReturn(new StockResponse("sku", 3, 0, 3));
        when(orderClient.quote("m", 5)).thenReturn(new PurchaseQuoteResponse("quote", 5,
                List.of(new PurchaseQuoteLineResponse("sku", 2, Money.krw(100), Money.krw(200))), Money.krw(200)));
    }

    @AfterEach
    void close() {
        queryExecutor.close();
    }

    /** [PD-0021-R1] Catalog 가격을 더하지 않고 Order의 견적을 그대로 표시한다. */
    @Test
    void assemblesTheCartUsingTheOrderQuote() {
        var page = executor.get("m");
        assertThat(page.quote().total()).isEqualTo(Money.krw(200));
        assertThat(page.lines().getFirst().productName()).isEqualTo("Product");
        assertThat(page.checkoutAllowed()).isTrue();
        assertThat(page.unavailable()).isEmpty();
    }

    /** [PD-0021-R4] Cart 오류를 빈 장바구니로 대체하지 않는다. */
    @Test
    void propagatesCartFailure() {
        when(cartClient.get("m")).thenThrow(DomainException.unavailable("cart"));
        assertThatThrownBy(() -> executor.get("m")).isInstanceOf(DomainException.class);
        verifyNoInteractions(catalogClient, inventoryClient, orderClient);
    }

    /** [PD-0021-R4] 상품 정보 실패는 수량을 보존하고 실패한 상품에 표시한다. */
    @Test
    void preservesTheCartWhenProductInformationIsUnavailable() {
        when(catalogClient.product("p")).thenThrow(DomainException.unavailable("catalog"));
        var page = executor.get("m");
        assertThat(page.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(page.lines().getFirst().informationAvailable()).isFalse();
        assertThat(page.unavailable()).contains("product:sku");
        assertThat(page.quote()).isNotNull();
    }

    /** [PD-0021-R4] 견적 미확인은 0원이 아니며 결제가 제한된다. */
    @Test
    void disablesCheckoutWithoutAQuote() {
        when(orderClient.quote("m", 5)).thenThrow(DomainException.unavailable("quote"));
        var page = executor.get("m");
        assertThat(page.quote()).isNull();
        assertThat(page.checkoutAllowed()).isFalse();
        assertThat(page.unavailable()).contains("quote");
    }

    /** [PD-0021-R4] 재고 미확인과 재고 부족을 구분하며 둘 다 결제가 제한된다. */
    @Test
    void disablesCheckoutForUnknownOrInsufficientStock() {
        when(inventoryClient.get("sku")).thenThrow(DomainException.unavailable("stock"));
        var unavailable = executor.get("m");
        assertThat(unavailable.checkoutAllowed()).isFalse();
        assertThat(unavailable.lines().getFirst().availableQuantity()).isNull();
        doReturn(new StockResponse("sku", 1, 0, 1)).when(inventoryClient).get("sku");
        var insufficient = executor.get("m");
        assertThat(insufficient.checkoutAllowed()).isFalse();
        assertThat(insufficient.lines().getFirst().availableQuantity()).isEqualTo(1);
        assertThat(insufficient.unavailable()).doesNotContain("inventory:sku");
    }

    @Test
    void rejectsAQuoteForAnotherCartVersion() {
        when(orderClient.quote("m", 5)).thenReturn(new PurchaseQuoteResponse(
                "quote", 6,
                List.of(new PurchaseQuoteLineResponse("sku", 2, Money.krw(100), Money.krw(200))),
                Money.krw(200)));

        var page = executor.get("m");

        assertThat(page.quote()).isNull();
        assertThat(page.checkoutAllowed()).isFalse();
        assertThat(page.unavailable()).contains("quote");
    }

    @Test
    void rejectsAPartialQuoteAtTheSameCartVersion() {
        when(orderClient.quote("m", 5)).thenReturn(new PurchaseQuoteResponse("quote", 5, List.of(), Money.krw(0)));

        var page = executor.get("m");

        assertThat(page.quote()).isNull();
        assertThat(page.checkoutAllowed()).isFalse();
        assertThat(page.unavailable()).contains("quote");
    }

    @Test
    void associatesPartialLookupResultsWithTheirOwnSku() {
        when(cartClient.get("m")).thenReturn(new CartResponse("m", List.of(
                new CartLineResponse("sku", 2),
                new CartLineResponse("sku_other", 1)
        ), 5));
        when(catalogClient.sku("sku_other")).thenThrow(DomainException.unavailable("other product"));
        when(inventoryClient.get("sku_other")).thenThrow(DomainException.unavailable("other stock"));
        when(orderClient.quote("m", 5)).thenReturn(new PurchaseQuoteResponse("quote", 5, List.of(
                new PurchaseQuoteLineResponse("sku_other", 1, Money.krw(50), Money.krw(50)),
                new PurchaseQuoteLineResponse("sku", 2, Money.krw(100), Money.krw(200))
        ), Money.krw(250)));

        var page = executor.get("m");
        var availableLine = page.lines().getFirst();
        var unavailableLine = page.lines().getLast();

        assertThat(availableLine.skuId()).isEqualTo("sku");
        assertThat(availableLine.availableQuantity()).isEqualTo(3);
        assertThat(availableLine.productName()).isEqualTo("Product");
        assertThat(unavailableLine.skuId()).isEqualTo("sku_other");
        assertThat(unavailableLine.availableQuantity()).isNull();
        assertThat(unavailableLine.informationAvailable()).isFalse();
        assertThat(page.unavailable()).containsExactly("product:sku_other", "inventory:sku_other");
        assertThat(page.quote().total()).isEqualTo(Money.krw(250));
        assertThat(page.checkoutAllowed()).isFalse();
    }

    @Test
    void returnsAnEmptyCartWithoutCallingUnneededDependencies() {
        when(cartClient.get("m")).thenReturn(new CartResponse("m", List.of(), 5));
        assertThat(executor.get("m").lines()).isEmpty();
        verifyNoInteractions(catalogClient, inventoryClient, orderClient);
    }
}
