package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import java.util.Comparator;
import java.util.List;

/** 견적과 주문이 공유하는 금액 구성 계산 및 구매 내용의 비교 값. */
public final class PurchasePricing {
    private PurchasePricing() {
    }

    public static PriceBreakdown priceBreakdown(List<OrderLine> lines) {
        var productAmount = productAmount(lines);
        var shippingFee = lines.isEmpty() ? Money.krw(0) : ShippingFeePolicy.shippingFee(productAmount);
        return PriceBreakdown.of(productAmount, shippingFee);
    }

    public static Money productAmount(List<OrderLine> lines) {
        if (lines.isEmpty()) {
            return Money.krw(0);
        }
        long amount = 0;
        for (var line : lines) {
            if (!"KRW".equals(line.unitPrice().currency())) {
                throw DomainException.validation("only KRW purchases are supported");
            }
            amount = Math.addExact(amount, line.lineTotal().amount());
        }
        return Money.krw(amount);
    }

    /** 서버가 현재 상태로 재계산한 값과 비교한다. 이 값을 가격 데이터로 해석하거나 신뢰하지 않는다. */
    public static String quoteId(String memberId, long version, List<OrderLine> lines) {
        var content = new StringBuilder().append(version);
        lines.stream()
                .sorted(Comparator.comparing(OrderLine::skuId))
                .forEach(line -> appendLine(content, line));
        var priceBreakdown = priceBreakdown(lines);
        appendMoney(content, priceBreakdown.productAmount());
        appendMoney(content, priceBreakdown.shippingFee());
        appendMoney(content, priceBreakdown.totalAmount());
        return CheckoutRequestFingerprint.from(memberId, content.toString()).value();
    }

    private static void appendLine(StringBuilder content, OrderLine line) {
        append(content, line.skuId());
        content.append(':').append(line.quantity()).append(':').append(line.unitPrice().amount());
        append(content, line.unitPrice().currency());
    }

    private static void appendMoney(StringBuilder content, Money money) {
        content.append(':').append(money.amount());
        append(content, money.currency());
    }

    private static void append(StringBuilder content, String value) {
        content.append(':').append(value.length()).append(':').append(value);
    }
}
