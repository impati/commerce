package com.impati.commerce.cart.application.port.in;

import java.util.List;

/** 체크아웃이 현재 장바구니에서 분리해 소유한 구매분. */
public record CheckoutCartDetails(String orderId, String memberId, List<CartLine> lines) {
}
