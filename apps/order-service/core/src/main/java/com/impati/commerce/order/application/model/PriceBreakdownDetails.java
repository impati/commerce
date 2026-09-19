package com.impati.commerce.order.application.model;

import com.impati.commerce.common.ApiContracts.Money;

/** 주문 도메인의 금액 구성을 유스케이스 결과로 옮긴 값. */
public record PriceBreakdownDetails(Money productAmount, Money shippingFee, Money totalAmount) {
}
