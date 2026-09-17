package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import java.util.Comparator;
import java.util.List;

/** 견적과 주문이 공유하는 상품 금액 계산 및 구매 내용의 비교 값. */
public final class PurchasePricing {
    private PurchasePricing() { }

    public static Money total(List<OrderLine> lines) {
        if (lines.isEmpty()) {
            return Money.krw(0);
        }
        var currency = lines.getFirst().unitPrice().currency();
        long amount = 0;
        for (var line : lines) {
            if (!currency.equals(line.unitPrice().currency())) {
                throw DomainException.validation("mixed currencies are not supported");
            }
            amount = Math.addExact(amount, line.lineTotal().amount());
        }
        return new Money(amount, currency);
    }

    /** 서버가 현재 상태로 재계산한 값과 비교한다. 이 값을 가격 데이터로 해석하거나 신뢰하지 않는다. */
    public static String quoteId(String memberId, long version, List<OrderLine> lines) {
        var content = new StringBuilder().append(version);
        lines.stream().sorted(Comparator.comparing(OrderLine::skuId)).forEach(line -> {
            append(content, line.skuId());
            content.append(':').append(line.quantity()).append(':').append(line.unitPrice().amount());
            append(content, line.unitPrice().currency());
        });
        var total = total(lines);
        content.append(':').append(total.amount());
        append(content, total.currency());
        return CheckoutRequestFingerprint.from(memberId, content.toString()).value();
    }

    private static void append(StringBuilder content, String value) {
        content.append(':').append(value.length()).append(':').append(value);
    }
}
