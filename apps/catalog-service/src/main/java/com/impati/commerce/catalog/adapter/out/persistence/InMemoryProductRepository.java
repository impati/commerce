package com.impati.commerce.catalog.adapter.out.persistence;

import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.Sku;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryProductRepository {
    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, Sku> skus = new ConcurrentHashMap<>();

    public void save(Product product) {
        products.put(product.id(), product);
        product.skus().forEach(sku -> skus.put(sku.id(), sku));
    }

    public Optional<Product> findById(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    public Optional<Sku> findSku(String skuId) {
        return Optional.ofNullable(skus.get(skuId));
    }

    public Collection<Product> findAll() {
        return products.values();
    }
}

