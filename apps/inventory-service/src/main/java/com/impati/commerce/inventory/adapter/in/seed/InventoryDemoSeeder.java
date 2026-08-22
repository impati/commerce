package com.impati.commerce.inventory.adapter.in.seed;

import com.impati.commerce.inventory.application.port.in.InventoryUseCase;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 데모 시드. 앱 시작이 트리거인 진입점이므로 컨트롤러와 같은 등급이고 {@code adapter/in}에 산다.
 *
 * <p>local 프로파일에서만 동작한다.
 */
@Configuration
@Profile("local")
public class InventoryDemoSeeder {
    @Bean
    ApplicationRunner seedStock(InventoryUseCase inventory) {
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
