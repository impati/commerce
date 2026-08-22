package com.impati.commerce.catalog.adapter.out.persistence;

import com.impati.commerce.catalog.application.port.out.ProductRepository;
import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.Sku;
import com.impati.commerce.catalog.domain.CatalogModels.SkuSpec;
import com.impati.commerce.common.ApiContracts.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트가 같은 DB를 공유하므로 SKU id를 테스트별로 다르게 만든다. 고정 id를 쓰면 두 번째
 * 테스트에서 PK 충돌이 난다. 빈 저장소를 가정하지 않는다는 규칙이 DB에서도 그대로 적용된다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalog-repo;DB_CLOSE_DELAY=-1")
class JdbcProductRepositoryTest {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsProductWithTagsSkusAndAttributes() {
        var product = publishedProduct("round");

        productRepository.save(product);
        var loaded = productRepository.findById(product.id()).orElseThrow();

        assertThat(loaded.name()).isEqualTo("Round Tee");
        assertThat(loaded.brand()).isEqualTo("Namu");
        assertThat(loaded.category()).isEqualTo("apparel");
        assertThat(loaded.description()).isEqualTo("round trip test");
        assertThat(loaded.status()).isEqualTo("PUBLISHED");
        assertThat(loaded.tags()).containsExactly("daily", "new");
        assertThat(loaded.skus().stream().map(Sku::id)).containsExactly("sku_round_white", "sku_round_black");
        assertThat(loaded.skus().getFirst().price()).isEqualTo(Money.krw(29_000));
        assertThat(loaded.skus().getFirst().attributes())
                .containsEntry("color", "white")
                .containsEntry("size", "M");
    }

    @Test
    void findsSkuAcrossProducts() {
        productRepository.save(publishedProduct("lookup"));

        var sku = productRepository.findSku("sku_lookup_black").orElseThrow();

        assertThat(sku.name()).isEqualTo("Black / L");
        assertThat(sku.price()).isEqualTo(Money.krw(31_000));
        assertThat(sku.attributes()).containsEntry("color", "black");
        assertThat(productRepository.findSku("sku_missing")).isEmpty();
    }

    /** 저장을 다시 하면 태그와 SKU가 중복되지 않고 교체돼야 한다. */
    @Test
    void savingAgainReplacesTagsAndSkus() {
        var product = publishedProduct("replace");
        productRepository.save(product);
        productRepository.save(product);

        var loaded = productRepository.findById(product.id()).orElseThrow();
        assertThat(loaded.tags()).containsExactly("daily", "new");
        assertThat(loaded.skus()).hasSize(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from sku_attributes where sku_id = ?", Integer.class, "sku_replace_white"))
                .isEqualTo(2);
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachSkuFieldToItsOwnColumn() {
        var product = publishedProduct("column");
        productRepository.save(product);

        var row = jdbc.queryForMap(
                "select product_id, sku_no, name, price_amount, price_currency, status"
                        + " from product_skus where id = ?",
                "sku_column_white"
        );
        assertThat(row.get("PRODUCT_ID")).isEqualTo(product.id());
        assertThat(row.get("SKU_NO")).isEqualTo(0);
        assertThat(row.get("NAME")).isEqualTo("White / M");
        assertThat(row.get("PRICE_AMOUNT")).isEqualTo(29_000L);
        assertThat(row.get("PRICE_CURRENCY")).isEqualTo("KRW");
        assertThat(row.get("STATUS")).isEqualTo("ACTIVE");
    }

    private Product publishedProduct(String suffix) {
        var product = new Product("Round Tee", "Namu", "apparel", "round trip test", List.of("daily", "new"));
        product.addSku(new SkuSpec("sku_" + suffix + "_white", "White / M", Money.krw(29_000),
                Map.of("color", "white", "size", "M")));
        product.addSku(new SkuSpec("sku_" + suffix + "_black", "Black / L", Money.krw(31_000),
                Map.of("color", "black", "size", "L")));
        product.publish();
        return product;
    }
}
