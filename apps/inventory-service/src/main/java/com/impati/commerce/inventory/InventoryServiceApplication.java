package com.impati.commerce.inventory;

import com.impati.commerce.inventory.application.InventoryService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner seedStock(InventoryService inventory) {
        return args -> {
            inventory.addStock("sku_tee_white_m", 20);
            inventory.addStock("sku_tee_black_l", 20);
            inventory.addStock("sku_drip_ivory", 20);
            inventory.addStock("sku_drip_moss", 20);
            inventory.addStock("sku_pouch_sage", 20);
        };
    }
}

