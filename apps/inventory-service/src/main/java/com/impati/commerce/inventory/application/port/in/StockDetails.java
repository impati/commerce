package com.impati.commerce.inventory.application.port.in;

/** 한 판매 단위의 재고. 가용 수량은 보유에서 예약을 뺀 값이다 (PD-0005-R1). */
public record StockDetails(String skuId, int onHand, int reserved, int available) {
}
