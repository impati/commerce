package com.impati.commerce.catalog.application.port.out;

import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.Sku;

import java.util.Collection;
import java.util.Optional;

/**
 * 상품 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface ProductRepository {
    void save(Product product);

    Optional<Product> findById(String productId);

    Optional<Sku> findSku(String skuId);

    Collection<Product> findAll();
}
