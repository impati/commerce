package com.impati.commerce.display.application;

import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.common.ApiContracts.DisplaySection;
import com.impati.commerce.common.ApiContracts.ProductCard;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DisplayService {
    private final CatalogClient catalog;

    public DisplayService(CatalogClient catalog) {
        this.catalog = catalog;
    }

    public DisplayHomeResponse home() {
        var products = catalog.products();
        return new DisplayHomeResponse(
                "Impati Market",
                "Curated products with reliable checkout and delivery.",
                List.of(
                        section("daily_essentials", "Daily Essentials", "daily", products),
                        section("new_arrivals", "New Arrivals", "new", products),
                        section("premium_picks", "Premium Picks", "premium", products)
                ).stream().filter(section -> !section.products().isEmpty()).toList()
        );
    }

    private DisplaySection section(String key, String title, String tag, List<ProductResponse> products) {
        var cards = products.stream()
                .filter(product -> product.tags().contains(tag))
                .map(this::card)
                .limit(8)
                .toList();
        return new DisplaySection(key, title, cards);
    }

    private ProductCard card(ProductResponse product) {
        var firstSku = product.skus().isEmpty() ? null : product.skus().getFirst();
        return new ProductCard(
                product.id(),
                product.name(),
                product.brand(),
                product.category(),
                firstSku == null ? null : firstSku.price(),
                product.tags()
        );
    }
}

