package com.impati.commerce.catalog.adapter.in.web;

import com.impati.commerce.catalog.application.port.in.ProductDetails;
import com.impati.commerce.catalog.application.port.in.SkuDetails;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;

/**
 * 유스케이스 결과를 서비스 간 HTTP 계약으로 옮긴다.
 *
 * <p>계약이 <b>서비스 간</b>의 것이므로 어댑터가 안다. 응용 계층이 알면 인바운드 어댑터가
 * 늘어날 때마다 응용이 바뀐다.
 */
final class CatalogResponseMapper {
    private CatalogResponseMapper() {
    }

    static SkuResponse from(SkuDetails sku) {
        return new SkuResponse(sku.id(), sku.productId(), sku.name(), sku.price(), sku.attributes(), sku.status());
    }

    static ProductResponse from(ProductDetails product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.brand(),
                product.category(),
                product.description(),
                product.status(),
                product.tags(),
                product.skus().stream().map(CatalogResponseMapper::from).toList()
        );
    }
}
