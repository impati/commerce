package com.impati.commerce.catalog.domain;

import com.impati.commerce.common.ApiContracts.Money;
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
            this(
                    spec.id() == null || spec.id().isBlank() ? Ids.newId("sku") : spec.id(),
                    productId,
                    spec.name(),
                    spec.price(),
                    spec.attributes(),
                    "ACTIVE"
            );
        }

        private Sku(
                String id,
                String productId,
                String name,
                Money price,
                Map<String, String> attributes,
                String status
        ) {
            this.id = id;
            this.productId = productId;
            this.name = required(name, "sku name is required");
            this.price = price;
            this.attributes = Map.copyOf(attributes);
            this.status = status;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static Sku restore(
                String id,
                String productId,
                String name,
                Money price,
                Map<String, String> attributes,
                String status
        ) {
            return new Sku(id, productId, name, price, attributes, status);
        }

        public String id() {
            return id;
        }

        public String productId() {
            return productId;
        }

        public String name() {
            return name;
        }

        public Money price() {
            return price;
        }

        public Map<String, String> attributes() {
            return attributes;
        }

        public String status() {
            return status;
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
            this(Ids.newId("prd"), name, brand, category, description, tags);
        }

        private Product(
                String id,
                String name,
                String brand,
                String category,
                String description,
                List<String> tags
        ) {
            this.id = id;
            this.name = required(name, "product name is required");
            this.brand = required(brand, "product brand is required");
            this.category = required(category, "product category is required");
            this.description = required(description, "product description is required");
            this.tags = List.copyOf(tags);
        }

        /**
         * 저장된 상태에서 복원한다.
         *
         * <p>publish를 거치지 않고 status를 그대로 세운다. 영속화 어댑터만 쓴다.
         */
        public static Product restore(
                String id,
                String name,
                String brand,
                String category,
                String description,
                List<String> tags,
                String status,
                List<Sku> skus
        ) {
            var product = new Product(id, name, brand, category, description, tags);
            product.status = status;
            product.skus.addAll(skus);
            return product;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String brand() {
            return brand;
        }

        public String category() {
            return category;
        }

        public String description() {
            return description;
        }

        public List<String> tags() {
            return tags;
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
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation(message);
        }
        return value;
    }
}
