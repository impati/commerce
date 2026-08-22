package com.impati.commerce.cart.application.port.in;

/** 장바구니에 담긴 한 줄. */
public record CartLine(String skuId, int quantity) {
}
