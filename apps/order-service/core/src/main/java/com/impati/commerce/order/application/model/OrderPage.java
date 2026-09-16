package com.impati.commerce.order.application.model;

import java.util.List;

public record OrderPage(List<OrderSummary> items, String nextCursor) {
}
