package com.impati.commerce.catalog.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;

import java.util.Map;

/**
 * 새로 만들 판매 단위. {@code id}가 비어 있으면 발급한다.
 *
 * <p>도메인 타입을 직접 받지 않는다. 유스케이스를 부르는 쪽이 도메인을 알아야 하면 그때부터
 * 도메인이 어댑터로 샌다.
 */
public record NewSku(String id, String name, Money price, Map<String, String> attributes) {
}
