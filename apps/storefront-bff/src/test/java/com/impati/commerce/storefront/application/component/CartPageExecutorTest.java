package com.impati.commerce.storefront.application.component;

import com.impati.commerce.common.ApiContracts.*;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.storefront.application.port.out.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartPageExecutorTest {
    private final CartClient cart = mock(CartClient.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final OrderClient order = mock(OrderClient.class);
    private ExecutorService queries;
    private CartPageExecutor executor;
    @BeforeEach void setUp() {
        queries = Executors.newVirtualThreadPerTaskExecutor();
        executor = new CartPageExecutor(cart, catalog, inventory, order, queries);
        when(cart.get("m")).thenReturn(new CartResponse("m", List.of(new CartLineResponse("sku", 2)), 5));
        when(catalog.sku("sku")).thenReturn(new SkuResponse("sku", "p", "Sku", Money.krw(1), Map.of(), "PUBLISHED"));
        when(catalog.product("p")).thenReturn(new ProductResponse("p", "Product", "B", "C", "D", "PUBLISHED", List.of(), List.of()));
        when(inventory.get("sku")).thenReturn(new StockResponse("sku", 3, 0, 3));
        when(order.quote("m", 5)).thenReturn(new PurchaseQuoteResponse("quote", 5,
                List.of(new PurchaseQuoteLineResponse("sku", 2, Money.krw(100), Money.krw(200))), Money.krw(200)));
    }
    @AfterEach void close() { queries.close(); }
    /** [PD-0021-R1] Catalog 가격을 더하지 않고 Order의 견적을 그대로 표시한다. */
    @Test void assemblesTheCartUsingTheOrderQuote() {
        var page = executor.get("m");
        assertThat(page.quote().total()).isEqualTo(Money.krw(200));
        assertThat(page.lines().getFirst().productName()).isEqualTo("Product");
        assertThat(page.checkoutAllowed()).isTrue();
        assertThat(page.unavailable()).isEmpty();
    }
    /** [PD-0021-R4] Cart 오류를 빈 장바구니로 대체하지 않는다. */
    @Test void propagatesCartFailure() {
        when(cart.get("m")).thenThrow(DomainException.unavailable("cart"));
        assertThatThrownBy(() -> executor.get("m")).isInstanceOf(DomainException.class);
        verifyNoInteractions(catalog, inventory, order);
    }
    /** [PD-0021-R4] 상품 정보 실패는 수량을 보존하고 실패한 상품에 표시한다. */
    @Test void preservesTheCartWhenProductInformationIsUnavailable() {
        when(catalog.product("p")).thenThrow(DomainException.unavailable("catalog"));
        var page = executor.get("m");
        assertThat(page.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(page.lines().getFirst().informationAvailable()).isFalse();
        assertThat(page.unavailable()).contains("product:sku");
        assertThat(page.quote()).isNotNull();
    }
    /** [PD-0021-R4] 견적 미확인은 0원이 아니며 결제가 제한된다. */
    @Test void disablesCheckoutWithoutAQuote() {
        when(order.quote("m", 5)).thenThrow(DomainException.unavailable("quote"));
        var page = executor.get("m");
        assertThat(page.quote()).isNull();
        assertThat(page.checkoutAllowed()).isFalse();
        assertThat(page.unavailable()).contains("quote");
    }
    /** [PD-0021-R4] 재고 미확인과 재고 부족을 구분하며 둘 다 결제가 제한된다. */
    @Test void disablesCheckoutForUnknownOrInsufficientStock() {
        when(inventory.get("sku")).thenThrow(DomainException.unavailable("stock"));
        var unavailable = executor.get("m");
        assertThat(unavailable.checkoutAllowed()).isFalse();
        assertThat(unavailable.lines().getFirst().availableQuantity()).isNull();
        doReturn(new StockResponse("sku", 1, 0, 1)).when(inventory).get("sku");
        var insufficient = executor.get("m");
        assertThat(insufficient.checkoutAllowed()).isFalse();
        assertThat(insufficient.lines().getFirst().availableQuantity()).isEqualTo(1);
        assertThat(insufficient.unavailable()).doesNotContain("inventory:sku");
    }
    @Test void rejectsAMixedVersionOrPartialQuote() {
        when(order.quote("m", 5)).thenReturn(new PurchaseQuoteResponse("quote", 6, List.of(), Money.krw(0)));
        var page = executor.get("m");
        assertThat(page.quote()).isNull();
        assertThat(page.checkoutAllowed()).isFalse();
    }
    @Test void returnsAnEmptyCartWithoutCallingUnneededDependencies() {
        when(cart.get("m")).thenReturn(new CartResponse("m", List.of(), 5));
        assertThat(executor.get("m").lines()).isEmpty();
        verifyNoInteractions(catalog, inventory, order);
    }
}
