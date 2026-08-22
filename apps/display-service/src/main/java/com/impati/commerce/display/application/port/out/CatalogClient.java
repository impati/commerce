package com.impati.commerce.display.application.port.out;

import com.impati.commerce.common.ApiContracts.ProductResponse;

import java.util.List;

/** catalog-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface CatalogClient {
    List<ProductResponse> products();
}
