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
        var original = List.of(line("a", 1, 100), line("b", 1, 200));
        assertThat(PurchasePricing.total(original)).isEqualTo(Money.krw(300));
        var id = PurchasePricing.quoteId("member", 3, original);
        assertThat(PurchasePricing.quoteId("member", 3, List.of(line("a", 1, 200), line("b", 1, 100)))).isNotEqualTo(id);
        assertThat(PurchasePricing.quoteId("member", 3, List.of(line("a", 1, 90), line("b", 1, 200)))).isNotEqualTo(id);
        assertThat(PurchasePricing.quoteId("member", 3, List.of(line("a", 1, 110), line("b", 1, 200)))).isNotEqualTo(id);
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
    void preservesCurrencyAndRejectsMixedCurrencies() {
        var usd = new OrderLine("a", "p", "P", "S", 2, new Money(100, "USD"));
        assertThat(PurchasePricing.total(List.of(usd))).isEqualTo(new Money(200, "USD"));
        assertThatThrownBy(() -> PurchasePricing.total(List.of(usd, line("b", 1, 100))))
                .isInstanceOf(DomainException.class);
    }
}
