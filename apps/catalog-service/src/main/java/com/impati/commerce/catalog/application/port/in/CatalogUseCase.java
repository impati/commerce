package com.impati.commerce.catalog.application.port.in;

import java.util.List;

/** 상품 카탈로그로 할 수 있는 일. */
public interface CatalogUseCase {
    ProductDetails createAndPublish(
            String name,
            String brand,
            String category,
            String description,
            List<String> tags,
            List<NewSku> skus
    );

    List<ProductDetails> list(String category, String query);

    ProductDetails getProduct(String productId);

    SkuDetails getSku(String skuId);

    /** 시드가 이미 들어가 있는지 확인한다. 파일 DB에서는 재시작마다 넣으면 상품이 늘어난다. */
    boolean isEmpty();
}
