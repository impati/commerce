package com.impati.commerce.storefront.application.port.out;

import com.impati.commerce.common.ApiContracts.*;

public interface InventoryClient {
    StockResponse get(String skuId);
}
