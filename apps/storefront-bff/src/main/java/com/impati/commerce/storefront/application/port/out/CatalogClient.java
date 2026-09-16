package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.*;

public interface CatalogClient {
    SkuResponse sku(String skuId);
    ProductResponse product(String productId);
}
