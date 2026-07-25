package com.impati.commerce.catalog.application;

import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.SkuSpec;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.DomainException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CatalogService {
    private final ProductRepository products;

    public CatalogService(ProductRepository products) {
        this.products = products;
    }

    public SkuSpec skuSpec(String name, Money price, Map<String, String> attributes) {
        return new SkuSpec(null, name, price, attributes);
    }

    public SkuSpec skuSpec(String id, String name, Money price, Map<String, String> attributes) {
        return new SkuSpec(id, name, price, attributes);
    }

    public ProductResponse createAndPublish(
            String name,
            String brand,
            String category,
            String description,
            List<String> tags,
            List<SkuSpec> skuSpecs
    ) {
        var product = new Product(name, brand, category, description, tags);
        skuSpecs.forEach(product::addSku);
        product.publish();
        products.save(product);
        return CatalogMapper.toResponse(product);
    }

    /** 시드가 이미 들어가 있는지 확인한다. 파일 DB에서는 재시작마다 시드를 넣으면 상품이 늘어난다. */
    public boolean isEmpty() {
        return products.findAll().isEmpty();
    }

    public List<ProductResponse> list(String category, String query) {
        return products.findAll().stream()
                .filter(product -> product.matches(category, query))
                .map(CatalogMapper::toResponse)
                .toList();
    }

    public ProductResponse getProduct(String productId) {
        return products.findById(productId)
                .filter(product -> product.status().equals("PUBLISHED"))
                .map(CatalogMapper::toResponse)
                .orElseThrow(() -> DomainException.notFound("product not found"));
    }

    public SkuResponse getSku(String skuId) {
        return products.findSku(skuId)
                .map(CatalogMapper::toResponse)
                .orElseThrow(() -> DomainException.notFound("sku not found"));
    }
}
