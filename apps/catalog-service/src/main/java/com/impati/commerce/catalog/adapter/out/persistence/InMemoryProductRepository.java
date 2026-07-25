package com.impati.commerce.catalog.adapter.out.persistence;

import com.impati.commerce.catalog.application.ProductRepository;
import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.Sku;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, Sku> skus = new ConcurrentHashMap<>();

    @Override
    public void save(Product product) {
        products.put(product.id(), product);
        product.skus().forEach(sku -> skus.put(sku.id(), sku));
    }

    @Override
    public Optional<Product> findById(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public Optional<Sku> findSku(String skuId) {
        return Optional.ofNullable(skus.get(skuId));
    }

    @Override
    public Collection<Product> findAll() {
        return products.values();
    }
}
