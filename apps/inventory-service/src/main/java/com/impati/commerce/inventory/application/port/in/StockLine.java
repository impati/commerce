package com.impati.commerce.inventory.application.port.in;

/** 한 판매 단위에 대한 수량. 예약 요청과 예약 내역 양쪽에 쓴다. */
public record StockLine(String skuId, int quantity) {
}
