package com.impati.commerce.display.application.component;

import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.display.application.port.in.DisplayUseCase;
import com.impati.commerce.display.application.port.in.HomePage;
import com.impati.commerce.display.application.port.in.HomeProductCard;
import com.impati.commerce.display.application.port.in.HomeSection;
import com.impati.commerce.display.application.port.out.CatalogClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DisplayExecutor implements DisplayUseCase {
    private final CatalogClient catalog;

    public DisplayExecutor(CatalogClient catalog) {
        this.catalog = catalog;
    }

    @Override
    public HomePage home() {
        var products = catalog.products();
        return new HomePage(
                "Impati Market",
                "Curated products with reliable checkout and delivery.",
                List.of(
                        section("daily_essentials", "Daily Essentials", "daily", products),
                        section("new_arrivals", "New Arrivals", "new", products),
                        section("premium_picks", "Premium Picks", "premium", products)
                ).stream().filter(section -> !section.products().isEmpty()).toList()
        );
    }

    private HomeSection section(String key, String title, String tag, List<ProductResponse> products) {
        var cards = products.stream()
                .filter(product -> product.tags().contains(tag))
                .map(this::card)
                .limit(8)
                .toList();
        return new HomeSection(key, title, cards);
    }

    private HomeProductCard card(ProductResponse product) {
        var firstSku = product.skus().isEmpty() ? null : product.skus().getFirst();
        return new HomeProductCard(
                product.id(),
                product.name(),
                product.brand(),
                product.category(),
                firstSku == null ? null : firstSku.price(),
                product.tags()
        );
    }
}
