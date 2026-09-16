package com.impati.commerce.order.application.port.in;

import java.util.List;

public record OrderPage(List<OrderSummary> items, String nextCursor) {
}
