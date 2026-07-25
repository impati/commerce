package com.impati.commerce.catalog.application;

import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.Sku;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다. 도메인은 계약을 모른다.
 */
final class CatalogMapper {
    private CatalogMapper() {
    }

    static SkuResponse toResponse(Sku sku) {
        return new SkuResponse(
                sku.id(),
                sku.productId(),
                sku.name(),
                sku.price(),
                sku.attributes(),
                sku.status()
        );
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.brand(),
                product.category(),
                product.description(),
                product.status(),
                product.tags(),
                product.skus().stream().map(CatalogMapper::toResponse).toList()
        );
    }
}
