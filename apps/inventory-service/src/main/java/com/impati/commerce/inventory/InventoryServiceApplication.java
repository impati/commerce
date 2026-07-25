package com.impati.commerce.inventory;

import com.impati.commerce.inventory.application.InventoryService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    /** 데모 시드. local 프로파일에서만 동작한다. 운영에 데모 데이터가 들어가지 않게 한다. */
    @Bean
    @Profile("local")
    ApplicationRunner seedStock(InventoryService inventory) {
        return args -> {
            // 파일 DB는 데이터가 남는다. 재시작마다 시드를 넣으면 재고가 계속 늘어난다.
            if (!inventory.isEmpty()) {
                return;
            }
            inventory.addStock("sku_tee_white_m", 20);
            inventory.addStock("sku_tee_black_l", 20);
            inventory.addStock("sku_drip_ivory", 20);
            inventory.addStock("sku_drip_moss", 20);
            inventory.addStock("sku_pouch_sage", 20);
        };
    }
}

