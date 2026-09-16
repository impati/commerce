package com.impati.commerce.order.application.port.in;

import com.impati.commerce.common.DomainException;

public record OrderQueryKey(

        String memberId,

        OrderCursor cursor,

        int size
) {
    public OrderQueryKey {
        if (memberId == null || memberId.isBlank() || size < 1 || size > 100) {
            throw DomainException.validation("order page size must be between 1 and 100");
        }
    }

    public static OrderQueryKey of(String memberId, String cursor, int size) {
        return new OrderQueryKey(memberId, OrderCursor.decode(cursor), size);
    }
}
