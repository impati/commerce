package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;

public interface CatalogClient {
    SkuResponse sku(String skuId);
    ProductResponse product(String productId);
}
