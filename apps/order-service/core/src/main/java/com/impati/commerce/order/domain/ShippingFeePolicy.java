package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import java.util.Objects;

/**
 * [PD-0023-R6] 주문 단위 무료배송 기준과 배송비를 한 곳에서 소유한다.
 */
public final class ShippingFeePolicy {

    private static final long FREE_SHIPPING_THRESHOLD = 50_000;
    private static final long STANDARD_SHIPPING_FEE = 3_000;

    private ShippingFeePolicy() {
    }

    public static Money shippingFee(Money productAmount) {
        Objects.requireNonNull(productAmount);
        if (!"KRW".equals(productAmount.currency())) {
            throw DomainException.validation("only KRW purchases are supported");
        }
        if (productAmount.amount() >= FREE_SHIPPING_THRESHOLD) {
            return Money.krw(0);
        }

        return Money.krw(STANDARD_SHIPPING_FEE);
    }
}
