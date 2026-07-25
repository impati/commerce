package com.impati.commerce.catalog.adapter.out.persistence;

import com.impati.commerce.catalog.application.ProductRepository;
import com.impati.commerce.catalog.domain.CatalogModels.Product;
import com.impati.commerce.catalog.domain.CatalogModels.Sku;
import com.impati.commerce.common.ApiContracts.Money;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 *
 * <p>태그와 SKU 속성은 별도 테이블로 정규화했다. JSON 컬럼에 넣으면 태그로 조회할 수 없다 —
 * 지면 섹션이 태그 기준으로 구성되므로 나중에 필요해질 조회 경로다.
 */
@Repository
public class JdbcProductRepository implements ProductRepository {
    private static final String PRODUCT_COLUMNS = "id, name, brand, category, description, status";

    private static final String INSERT_PRODUCT = """
            insert into products (id, name, brand, category, description, status)
            values (:id, :name, :brand, :category, :description, :status)
            """;

    private static final String UPDATE_PRODUCT = """
            update products
               set name = :name,
                   brand = :brand,
                   category = :category,
                   description = :description,
                   status = :status
             where id = :id
            """;

    private static final String SELECT_PRODUCT = "select " + PRODUCT_COLUMNS + " from products where id = :id";
    private static final String SELECT_PRODUCTS = "select " + PRODUCT_COLUMNS + " from products order by id";

    private static final String DELETE_TAGS = "delete from product_tags where product_id = :product_id";
    private static final String INSERT_TAG = """
            insert into product_tags (product_id, tag_no, tag)
            values (:product_id, :tag_no, :tag)
            """;
    private static final String SELECT_TAGS =
            "select tag from product_tags where product_id = :product_id order by tag_no";

    private static final String DELETE_SKU_ATTRIBUTES = """
            delete from sku_attributes
             where sku_id in (select id from product_skus where product_id = :product_id)
            """;
    private static final String DELETE_SKUS = "delete from product_skus where product_id = :product_id";
    private static final String INSERT_SKU = """
            insert into product_skus (id, product_id, sku_no, name, price_amount, price_currency, status)
            values (:id, :product_id, :sku_no, :name, :price_amount, :price_currency, :status)
            """;
    private static final String INSERT_SKU_ATTRIBUTE = """
            insert into sku_attributes (sku_id, attr_key, attr_value)
            values (:sku_id, :attr_key, :attr_value)
            """;
    private static final String SELECT_SKUS = """
            select id, product_id, name, price_amount, price_currency, status
              from product_skus
             where product_id = :product_id
             order by sku_no
            """;
    private static final String SELECT_SKU = """
            select id, product_id, name, price_amount, price_currency, status
              from product_skus
             where id = :id
            """;
    private static final String SELECT_SKU_ATTRIBUTES = """
            select attr_key, attr_value from sku_attributes where sku_id = :sku_id order by attr_key
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProductRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Product product) {
        var productId = new MapSqlParameterSource("product_id", product.id());
        var params = new MapSqlParameterSource()
                .addValue("id", product.id())
                .addValue("name", product.name())
                .addValue("brand", product.brand())
                .addValue("category", product.category())
                .addValue("description", product.description())
                .addValue("status", product.status());
        if (jdbc.update(UPDATE_PRODUCT, params) == 0) {
            jdbc.update(INSERT_PRODUCT, params);
        }

        jdbc.update(DELETE_TAGS, productId);
        var tags = product.tags();
        for (var index = 0; index < tags.size(); index++) {
            jdbc.update(INSERT_TAG, new MapSqlParameterSource()
                    .addValue("product_id", product.id())
                    .addValue("tag_no", index)
                    .addValue("tag", tags.get(index)));
        }

        jdbc.update(DELETE_SKU_ATTRIBUTES, productId);
        jdbc.update(DELETE_SKUS, productId);
        var skus = product.skus();
        for (var index = 0; index < skus.size(); index++) {
            var sku = skus.get(index);
            jdbc.update(INSERT_SKU, new MapSqlParameterSource()
                    .addValue("id", sku.id())
                    .addValue("product_id", product.id())
                    .addValue("sku_no", index)
                    .addValue("name", sku.name())
                    .addValue("price_amount", sku.price().amount())
                    .addValue("price_currency", sku.price().currency())
                    .addValue("status", sku.status()));
            sku.attributes().forEach((key, value) -> jdbc.update(INSERT_SKU_ATTRIBUTE,
                    new MapSqlParameterSource()
                            .addValue("sku_id", sku.id())
                            .addValue("attr_key", key)
                            .addValue("attr_value", value)));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(String productId) {
        return jdbc.query(SELECT_PRODUCT, new MapSqlParameterSource("id", productId), productMapper())
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sku> findSku(String skuId) {
        return jdbc.query(SELECT_SKU, new MapSqlParameterSource("id", skuId), skuMapper())
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Product> findAll() {
        return jdbc.query(SELECT_PRODUCTS, productMapper());
    }

    private RowMapper<Product> productMapper() {
        return (rs, rowNum) -> {
            var id = rs.getString("id");
            return Product.restore(
                    id,
                    rs.getString("name"),
                    rs.getString("brand"),
                    rs.getString("category"),
                    rs.getString("description"),
                    findTags(id),
                    rs.getString("status"),
                    findSkus(id)
            );
        };
    }

    private RowMapper<Sku> skuMapper() {
        return (rs, rowNum) -> {
            var id = rs.getString("id");
            return Sku.restore(
                    id,
                    rs.getString("product_id"),
                    rs.getString("name"),
                    new Money(rs.getLong("price_amount"), rs.getString("price_currency")),
                    findAttributes(id),
                    rs.getString("status")
            );
        };
    }

    private List<String> findTags(String productId) {
        return jdbc.queryForList(SELECT_TAGS, new MapSqlParameterSource("product_id", productId), String.class);
    }

    private List<Sku> findSkus(String productId) {
        return jdbc.query(SELECT_SKUS, new MapSqlParameterSource("product_id", productId), skuMapper());
    }

    private Map<String, String> findAttributes(String skuId) {
        Map<String, String> attributes = new LinkedHashMap<>();
        jdbc.query(SELECT_SKU_ATTRIBUTES, new MapSqlParameterSource("sku_id", skuId), rs -> {
            attributes.put(rs.getString("attr_key"), rs.getString("attr_value"));
        });
        return attributes;
    }
}
