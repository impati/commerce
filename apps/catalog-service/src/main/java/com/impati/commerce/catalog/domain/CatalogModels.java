package com.impati.commerce.catalog.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CatalogModels {
    private CatalogModels() {
    }

    public record SkuSpec(String id, String name, Money price, Map<String, String> attributes) {
    }

    public static final class Sku {
        private final String id;
        private final String productId;
        private final String name;
        private final Money price;
        private final Map<String, String> attributes;
        private final String status;

        Sku(String productId, SkuSpec spec) {
            this.id = spec.id() == null || spec.id().isBlank() ? Ids.newId("sku") : spec.id();
            this.productId = productId;
            this.name = required(spec.name(), "sku name is required");
            this.price = spec.price();
            this.attributes = Map.copyOf(spec.attributes());
            this.status = "ACTIVE";
        }

        public String id() {
            return id;
        }

        public String productId() {
            return productId;
        }

        public SkuResponse toResponse() {
            return new SkuResponse(id, productId, name, price, attributes, status);
        }
    }

    public static final class Product {
        private final String id;
        private final String name;
        private final String brand;
        private final String category;
        private final String description;
        private final List<String> tags;
        private final List<Sku> skus = new ArrayList<>();
        private String status = "DRAFT";

        public Product(String name, String brand, String category, String description, List<String> tags) {
            this.id = Ids.newId("prd");
            this.name = required(name, "product name is required");
            this.brand = required(brand, "product brand is required");
            this.category = required(category, "product category is required");
            this.description = required(description, "product description is required");
            this.tags = List.copyOf(tags);
        }

        public String id() {
            return id;
        }

        public String status() {
            return status;
        }

        public List<Sku> skus() {
            return List.copyOf(skus);
        }

        public void addSku(SkuSpec spec) {
            skus.add(new Sku(id, spec));
        }

        public void publish() {
            if (skus.isEmpty()) {
                throw DomainException.validation("product requires at least one sku");
            }
            status = "PUBLISHED";
        }

        public boolean matches(String categoryFilter, String query) {
            var categoryMatches = categoryFilter == null || category.equalsIgnoreCase(categoryFilter);
            var normalized = query == null ? "" : query.toLowerCase();
            var queryMatches = normalized.isBlank()
                    || name.toLowerCase().contains(normalized)
                    || brand.toLowerCase().contains(normalized)
                    || String.join(" ", tags).toLowerCase().contains(normalized);
            return status.equals("PUBLISHED") && categoryMatches && queryMatches;
        }

        public ProductResponse toResponse() {
            return new ProductResponse(
                    id,
                    name,
                    brand,
                    category,
                    description,
                    status,
                    tags,
                    skus.stream().map(Sku::toResponse).toList()
            );
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation(message);
        }
        return value;
    }
}
