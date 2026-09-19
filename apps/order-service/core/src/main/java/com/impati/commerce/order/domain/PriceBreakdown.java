package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import java.util.Objects;

/** 주문 시점에 확정되는 상품 금액, 배송비와 최종 결제 금액. */
public record PriceBreakdown(Money productAmount, Money shippingFee, Money totalAmount) {
    private static final String KRW = "KRW";

    public PriceBreakdown {
        Objects.requireNonNull(productAmount);
        Objects.requireNonNull(shippingFee);
        Objects.requireNonNull(totalAmount);
        requireKrw(productAmount);
        requireKrw(shippingFee);
        requireKrw(totalAmount);
        if (productAmount.amount() < 0 || shippingFee.amount() < 0 || totalAmount.amount() < 0) {
            throw DomainException.validation("purchase amounts must not be negative");
        }
        if (Math.addExact(productAmount.amount(), shippingFee.amount()) != totalAmount.amount()) {
            throw DomainException.validation("purchase total must equal product amount plus shipping fee");
        }
    }

    public static PriceBreakdown of(Money productAmount, Money shippingFee) {
        return new PriceBreakdown(
                productAmount,
                shippingFee,
                Money.krw(Math.addExact(productAmount.amount(), shippingFee.amount()))
        );
    }

    private static void requireKrw(Money money) {
        if (!KRW.equals(money.currency())) {
            throw DomainException.validation("only KRW purchases are supported");
        }
    }
}
