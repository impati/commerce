package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;

/** catalog-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface CatalogClient {
    SkuResponse sku(String skuId);

    ProductResponse product(String productId);
}
