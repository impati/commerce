package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.StockResponse;

public interface InventoryClient {
    StockResponse get(String skuId);
}
