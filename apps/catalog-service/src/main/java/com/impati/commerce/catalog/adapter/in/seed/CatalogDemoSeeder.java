package com.impati.commerce.catalog.adapter.in.seed;

import com.impati.commerce.catalog.application.port.in.CatalogUseCase;
import com.impati.commerce.catalog.application.port.in.NewSku;
import com.impati.commerce.common.ApiContracts.Money;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;

/**
 * 데모 시드. 앱 시작이 트리거인 진입점이므로 컨트롤러와 같은 등급이고 {@code adapter/in}에 산다.
 *
 * <p>local 프로파일에서만 동작한다. 알려진 데이터가 운영에 존재할 수 없게 하려는 것이다.
 */
@Configuration
@Profile("local")
public class CatalogDemoSeeder {
    @Bean
    ApplicationRunner seedCatalog(CatalogUseCase catalog) {
        return args -> {
            // 파일 DB는 데이터가 남는다. 재시작마다 시드를 넣으면 상품이 계속 늘어난다.
            if (!catalog.isEmpty()) {
                return;
            }
            catalog.createAndPublish(
                    "Everyday Cotton Tee",
                    "Namu",
                    "apparel",
                    "Soft tee for daily wear.",
                    List.of("daily", "new"),
                    List.of(
                            new NewSku("sku_tee_white_m", "White / M", Money.krw(29000),
                                    Map.of("color", "white", "size", "M")),
                            new NewSku("sku_tee_black_l", "Black / L", Money.krw(29000),
                                    Map.of("color", "black", "size", "L"))
                    )
            );
            catalog.createAndPublish(
                    "Ceramic Drip Set",
                    "Slowbrew",
                    "home",
                    "Pour-over set with ceramic dripper and server.",
                    List.of("daily", "premium"),
                    List.of(
                            new NewSku("sku_drip_ivory", "Ivory", Money.krw(87000), Map.of("color", "ivory")),
                            new NewSku("sku_drip_moss", "Moss", Money.krw(91000), Map.of("color", "moss"))
                    )
            );
            catalog.createAndPublish(
                    "Compact Travel Pouch",
                    "Rove",
                    "travel",
                    "Water-resistant pouch with internal dividers.",
                    List.of("new"),
                    List.of(new NewSku("sku_pouch_sage", "Sage", Money.krw(34000), Map.of("color", "sage")))
            );
        };
    }
}
