package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;

/** 반품 식별자에 귀속된 금액 지정 환불의 현재 결과. */
public record ReturnRefundDetails(String returnId, String paymentId, Money amount, String status) { }
