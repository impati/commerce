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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    private final CartClient cartClient = mock(CartClient.class);
    private final CatalogClient catalogClient = mock(CatalogClient.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CheckoutProgressRepository checkoutProgressRepository = mock(CheckoutProgressRepository.class);
    private final CheckoutChanges checkoutChanges = mock(CheckoutChanges.class);
    private final MemberClient memberClient = mock(MemberClient.class);
    private OrderExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OrderExecutor(
                orderRepository,
                checkoutProgressRepository,
                checkoutChanges,
                mock(OrderChanges.class),
                mock(CheckoutExecution.class),
                memberClient,
                cartClient,
                catalogClient,
                mock(PaymentClient.class),
                mock(ShippingClient.class),
                Clock.systemUTC()
        );
        when(memberClient.member("m")).thenReturn(new MemberResponse("m", "m@example.test", "Member", "ACTIVE", List.of(
                new AddressResponse("a", "home", "Member", "010-0000-0000", "Road", "Seoul", "00000", true))));
        when(cartClient.cart("m")).thenReturn(new CartResponse("m", List.of(new CartLineResponse("sku", 2)), 5));
        when(catalogClient.sku("sku")).thenReturn(sku(100));
        when(catalogClient.product("p")).thenReturn(new ProductResponse(
                "p", "Product", "B", "C", "D", "PUBLISHED", List.of(), List.of()));
    }

    /** [PD-0021-R2] 가격이 오르거나 내려도 접수 전에 거절하며 주문이나 구매분을 만들지 않는다. */
    @ParameterizedTest
    @ValueSource(longs = {90, 110})
    void rejectsChangedPricesBeforeCreatingAnOrder(long changedPrice) {
        var quote = executor.quote("m", 5);
        when(catalogClient.sku("sku")).thenReturn(sku(changedPrice));

        assertThatThrownBy(() -> executor.checkoutConfirmed(
                "m", new IdempotencyKey("key"), "card", "a", quote.id(), address().confirmationToken()))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("quote_changed"));

        verifyNoInteractions(orderRepository, checkoutChanges, memberClient);
        verify(cartClient, never()).checkout(anyString(), anyString(), anyLong());
    }

    /** [PD-0021-R3] 수량 변경 후 원래 수량으로 돌려도 이전 버전의 견적으로 접수하지 않는다. */
    @Test
    void rejectsAChangedCartVersionEvenWithIdenticalContents() {
        var quote = executor.quote("m", 5);
        when(cartClient.cart("m")).thenReturn(new CartResponse("m", List.of(new CartLineResponse("sku", 2)), 7));
        assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card", "a", quote.id(), address().confirmationToken()))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("quote_changed"));
        assertThatThrownBy(() -> executor.quote("m", 5)).isInstanceOf(DomainException.class);
        verifyNoInteractions(orderRepository, checkoutChanges, memberClient);
    }

    @Test
    void rejectsMissingQuoteWithoutReadingOtherServices() {
        assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card", "a", null, address().confirmationToken()))
                .isInstanceOf(DomainException.class);
        verifyNoInteractions(cartClient, catalogClient, memberClient, checkoutChanges);
    }

    /** [PD-0022-R6] 화면의 확인값과 다른 배송 정보를 접수 전에 거절한다. */
    @Test
    void rejectsChangedDeliveryInformationBeforeCreatingAnOrder() {
        var quote = executor.quote("m", 5);
        var changed = new AddressResponse("a", "home", "Different recipient", "010-0000-0000",
                "Road", "Seoul", "00000", true);
        when(memberClient.member("m")).thenReturn(new MemberResponse("m", "m@example.test", "Member", "ACTIVE", List.of(changed)));
        assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card",
                "a", quote.id(), address().confirmationToken()))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("address_changed"));
        verifyNoInteractions(checkoutChanges, orderRepository);
    }

    /** [PD-0022-R1, PD-0022-R3, PD-0022-R6] 삭제·타인 배송지·누락 확인값은 새 주문에 사용할 수 없다. */
    @Test
    void rejectsDeletedForeignAndUnconfirmedAddresses() {
        var quote = executor.quote("m", 5);
        for (var id : new String[]{"foreign", "deleted", "", null}) {
            assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card",
                    id, quote.id(), address().confirmationToken())).isInstanceOf(DomainException.class);
        }
        assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card",
                "a", quote.id(), null)).isInstanceOf(DomainException.class);
        when(memberClient.member("m")).thenReturn(new MemberResponse("m", "m@example.test", "Member", "ACTIVE", List.of()));
        assertThatThrownBy(() -> executor.checkoutConfirmed("m", new IdempotencyKey("key"), "card",
                "a", quote.id(), address().confirmationToken())).isInstanceOf(DomainException.class);
        verifyNoInteractions(checkoutChanges, orderRepository);
    }

    /** [PD-0022-R6] 확인값은 배송 필드 각각에 반응하고 별칭·기본 지정은 무시한다. */
    @Test
    void confirmationTokenTracksOnlyDeliveryFieldsAndIdentity() {
        var original = address();
        var metadata = new AddressResponse("a", "renamed", "Member", "010-0000-0000", "Road", "Seoul", "00000", false);
        assertThat(metadata.confirmationToken()).isEqualTo(original.confirmationToken());
        var values = new String[]{"a", "Member", "010-0000-0000", "Road", "Seoul", "00000"};
        for (var index = 0; index < values.length; index++) {
            var changed = values.clone();
            changed[index] += "changed";
            var candidate = new AddressResponse(changed[0], "home", changed[1], changed[2], changed[3], changed[4], changed[5], true);
            assertThat(candidate.confirmationToken()).isNotEqualTo(original.confirmationToken());
        }
    }

    private AddressResponse address() {
        return new AddressResponse("a", "home", "Member", "010-0000-0000", "Road", "Seoul", "00000", true);
    }

    private SkuResponse sku(long price) {
        return new SkuResponse("sku", "p", "Sku", Money.krw(price), Map.of(), "PUBLISHED");
    }
}
