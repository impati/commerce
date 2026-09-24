package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchasePricingTest {
    private OrderLine line(String sku, int quantity, long amount) {
        return new OrderLine(sku, "product", "Product", "Sku", quantity, Money.krw(amount));
    }

    /** [PD-0021-R1, PD-0021-R2] 금액 합산과 증감 모두 견적 변경으로 판별한다. */
    @Test
    void detectsPriceChangesEvenWhenTheTotalIsUnchanged() {
        var original = List.of(line("a", 1, 20_000), line("b", 1, 30_000));
        assertThat(PurchasePricing.priceBreakdown(original).totalAmount()).isEqualTo(Money.krw(50_000));
        var id = PurchasePricing.quoteId("member", 3, original);
        assertThat(PurchasePricing.quoteId("member", 3,
                List.of(line("a", 1, 30_000), line("b", 1, 20_000)))).isNotEqualTo(id);
        assertThat(PurchasePricing.quoteId("member", 3,
                List.of(line("a", 1, 19_000), line("b", 1, 30_000)))).isNotEqualTo(id);
        assertThat(PurchasePricing.quoteId("member", 3,
                List.of(line("a", 1, 21_000), line("b", 1, 30_000)))).isNotEqualTo(id);
    }

    /** [PD-0021-R3] 같은 금액이어도 회원, 버전, 상품과 수량이 다른 견적은 구분된다. */
    @Test
    void bindsTheMemberVersionAndPurchaseContents() {
        var lines = List.of(line("a", 2, 100));
        var id = PurchasePricing.quoteId("member", 3, lines);
        assertThat(PurchasePricing.quoteId("other", 3, lines)).isNotEqualTo(id);
        assertThat(PurchasePricing.quoteId("member", 4, lines)).isNotEqualTo(id);
        assertThat(PurchasePricing.quoteId("member", 3, List.of(line("a", 1, 200)))).isNotEqualTo(id);
        assertThat(PurchasePricing.quoteId("member", 3, List.of(line("b", 2, 100)))).isNotEqualTo(id);
    }

    @Test
    void rejectsNonKrwPurchases() {
        var usd = new OrderLine("a", "p", "P", "S", 2, new Money(100, "USD"));
        assertThatThrownBy(() -> PurchasePricing.priceBreakdown(List.of(usd)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PurchasePricing.priceBreakdown(List.of(usd, line("b", 1, 100))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ShippingFeePolicy.shippingFee(new Money(50_000, "USD")))
                .isInstanceOf(DomainException.class);
    }

    /** [PD-0026-R8, PD-0026-R9] 50,000원 경계에서 배송비와 최종 금액이 함께 바뀐다. */
    @Test
    void appliesShippingFeeAtTheFreeShippingBoundary() {
        assertThat(PurchasePricing.priceBreakdown(List.of(line("a", 1, 49_999))))
                .isEqualTo(new PriceBreakdown(Money.krw(49_999), Money.krw(3_000), Money.krw(52_999)));
        assertThat(PurchasePricing.priceBreakdown(List.of(line("a", 1, 50_000))))
                .isEqualTo(new PriceBreakdown(Money.krw(50_000), Money.krw(0), Money.krw(50_000)));
        assertThat(PurchasePricing.priceBreakdown(List.of()))
                .isEqualTo(new PriceBreakdown(Money.krw(0), Money.krw(0), Money.krw(0)));
    }

    /** [PD-0026-R9] 합계가 상품 금액과 배송비의 합이 아니면 금액 구성을 만들 수 없다. */
    @Test
    void rejectsAnInconsistentPriceBreakdown() {
        assertThatThrownBy(() -> new PriceBreakdown(Money.krw(49_999), Money.krw(3_000), Money.krw(49_999)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PriceBreakdown(
                new Money(49_999, "USD"), Money.krw(3_000), Money.krw(52_999)))
                .isInstanceOf(DomainException.class);
    }
}
