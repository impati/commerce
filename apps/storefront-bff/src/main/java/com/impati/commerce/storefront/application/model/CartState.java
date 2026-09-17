package com.impati.commerce.storefront.application.model;

import com.impati.commerce.common.ApiContracts.CartLineResponse;
import java.util.List;

/** Cart가 확정한 변경 결과. 후속 화면 조회의 성공 여부와 별도로 전달한다. */
public record CartState(String memberId, long version, List<CartLineResponse> lines) {
}
