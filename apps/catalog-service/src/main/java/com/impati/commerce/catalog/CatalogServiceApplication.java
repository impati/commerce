package com.impati.commerce.catalog;

import com.impati.commerce.catalog.application.CatalogService;
import com.impati.commerce.common.ApiContracts.Money;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class CatalogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner seedCatalog(CatalogService catalog) {
        return args -> {
            catalog.createAndPublish(
                    "Everyday Cotton Tee",
                    "Namu",
                    "apparel",
                    "Soft tee for daily wear.",
                    List.of("daily", "new"),
                    List.of(
                            catalog.skuSpec("sku_tee_white_m", "White / M", Money.krw(29000), Map.of("color", "white", "size", "M")),
                            catalog.skuSpec("sku_tee_black_l", "Black / L", Money.krw(29000), Map.of("color", "black", "size", "L"))
                    )
            );
            catalog.createAndPublish(
                    "Ceramic Drip Set",
                    "Slowbrew",
                    "home",
                    "Pour-over set with ceramic dripper and server.",
                    List.of("daily", "premium"),
                    List.of(
                            catalog.skuSpec("sku_drip_ivory", "Ivory", Money.krw(87000), Map.of("color", "ivory")),
                            catalog.skuSpec("sku_drip_moss", "Moss", Money.krw(91000), Map.of("color", "moss"))
                    )
            );
            catalog.createAndPublish(
                    "Compact Travel Pouch",
                    "Rove",
                    "travel",
                    "Water-resistant pouch with internal dividers.",
                    List.of("new"),
                    List.of(catalog.skuSpec("sku_pouch_sage", "Sage", Money.krw(34000), Map.of("color", "sage")))
            );
        };
    }
}
