package com.impati.commerce.catalog.application.component;

import com.impati.commerce.catalog.application.port.in.ProductDetails;
import com.impati.commerce.catalog.application.port.in.SkuDetails;
import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.Sku;

/**
 * 도메인 모델을 유스케이스 결과로 옮긴다. 도메인은 결과 타입을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 볼 수 있으면 도메인 객체를 손에 넣어야 부를 수
 * 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 */
final class CatalogMapper {
    private CatalogMapper() {
    }

    static SkuDetails toDetails(Sku sku) {
        return new SkuDetails(
                sku.id(),
                sku.productId(),
                sku.name(),
                sku.price(),
                sku.attributes(),
                sku.status()
        );
    }

    static ProductDetails toDetails(Product product) {
        return new ProductDetails(
                product.id(),
                product.name(),
                product.brand(),
                product.category(),
                product.description(),
                product.status(),
                product.tags(),
                product.skus().stream().map(CatalogMapper::toDetails).toList()
        );
    }
}
