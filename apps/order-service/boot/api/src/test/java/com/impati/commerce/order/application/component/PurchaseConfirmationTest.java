package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.CartClient;
import com.impati.commerce.order.application.port.out.CatalogClient;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.MemberClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.IdempotencyKey;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseConfirmationTest {
    private final CartClient cart = mock(CartClient.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CheckoutProgressRepository progress = mock(CheckoutProgressRepository.class);
    private final CheckoutChanges changes = mock(CheckoutChanges.class);
    private final MemberClient member = mock(MemberClient.class);
    private OrderExecutor executor;
    @BeforeEach
    void setUp() {
        executor = new OrderExecutor(orders, progress, changes, mock(OrderChanges.class), mock(CheckoutExecution.class),
                member, cart, catalog, mock(PaymentClient.class), mock(ShippingClient.class), Clock.systemUTC());
        when(member.member("m")).thenReturn(new MemberResponse("m", "m@example.test", "Member", "ACTIVE", List.of(
                new AddressResponse("a", "home", "Member", "010-0000-0000", "Road", "Seoul", "00000", true))));
        when(cart.cart("m")).thenReturn(new CartResponse("m", List.of(new CartLineResponse("sku", 2)), 5));
        when(catalog.sku("sku")).thenReturn(new SkuResponse("sku", "p", "Sku", Money.krw(100), Map.of(), "PUBLISHED"));
        when(catalog.product("p")).thenReturn(new ProductResponse("p", "Product", "B", "C", "D", "PUBLISHED", List.of(), List.of()));
    }
    /** [PD-0021-R2] 가격이 오르거나 내려도 접수 전에 거절하며 주문이나 구매분을 만들지 않는다. */
    @Test
    void rejectsChangedPricesBeforeCreatingAnOrder() {
        var quote = executor.quote("m", 5);
        for (long price : new long[]{90, 110}) {
            when(catalog.sku("sku")).thenReturn(new SkuResponse("sku", "p", "Sku", Money.krw(price), Map.of(), "PUBLISHED"));
            assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card", null, quote.id()))
                    .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("quote_changed"));
        }
        verifyNoInteractions(orders, changes, member);
        verify(cart, never()).checkout(anyString(), anyString(), anyLong());
    }
    /** [PD-0021-R3] 수량 변경 후 원래 수량으로 돌려도 이전 버전의 견적으로 접수하지 않는다. */
    @Test
    void rejectsAChangedCartVersionEvenWithIdenticalContents() {
        var quote = executor.quote("m", 5);
        when(cart.cart("m")).thenReturn(new CartResponse("m", List.of(new CartLineResponse("sku", 2)), 7));
        assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card", null, quote.id()))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("quote_changed"));
        assertThatThrownBy(() -> executor.quote("m", 5)).isInstanceOf(DomainException.class);
        verifyNoInteractions(orders, changes, member);
    }
    @Test
    void rejectsMissingQuoteWithoutReadingOtherServices() {
        assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card", null, null))
                .isInstanceOf(DomainException.class);
        verifyNoInteractions(cart, catalog, member, changes);
    }
}
