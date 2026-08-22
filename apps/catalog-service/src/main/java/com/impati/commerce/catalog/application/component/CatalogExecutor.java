package com.impati.commerce.catalog.application.component;

import com.impati.commerce.catalog.application.port.in.CatalogUseCase;
import com.impati.commerce.catalog.application.port.in.NewSku;
import com.impati.commerce.catalog.application.port.in.ProductDetails;
import com.impati.commerce.catalog.application.port.in.SkuDetails;
import com.impati.commerce.catalog.application.port.out.ProductRepository;
import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.SkuSpec;
import com.impati.commerce.common.DomainException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatalogExecutor implements CatalogUseCase {
    private final ProductRepository productRepository;

    public CatalogExecutor(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductDetails createAndPublish(
            String name,
            String brand,
            String category,
            String description,
            List<String> tags,
            List<NewSku> skus
    ) {
        var product = new Product(name, brand, category, description, tags);
        skus.stream()
                .map(sku -> new SkuSpec(sku.id(), sku.name(), sku.price(), sku.attributes()))
                .forEach(product::addSku);
        product.publish();
        productRepository.save(product);
        return CatalogMapper.toDetails(product);
    }

    @Override
    public boolean isEmpty() {
        return productRepository.findAll().isEmpty();
    }

    @Override
    public List<ProductDetails> list(String category, String query) {
        return productRepository.findAll().stream()
                .filter(product -> product.matches(category, query))
                .map(CatalogMapper::toDetails)
                .toList();
    }

    @Override
    public ProductDetails getProduct(String productId) {
        return productRepository.findById(productId)
                .filter(product -> product.status().equals("PUBLISHED"))
                .map(CatalogMapper::toDetails)
                .orElseThrow(() -> DomainException.notFound("product not found"));
    }

    @Override
    public SkuDetails getSku(String skuId) {
        return productRepository.findSku(skuId)
                .map(CatalogMapper::toDetails)
                .orElseThrow(() -> DomainException.notFound("sku not found"));
    }
}
