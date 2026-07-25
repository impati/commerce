package com.impati.commerce.cart.application;

import com.impati.commerce.common.ApiContracts.SkuResponse;

/**
 * catalog-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다.
 *
 * <p>장바구니에 담기 전 SKU가 실재하는지 확인하는 용도다. 없으면 예외가 올라온다.
 */
public interface CatalogClient {
    SkuResponse getSku(String skuId);
}
