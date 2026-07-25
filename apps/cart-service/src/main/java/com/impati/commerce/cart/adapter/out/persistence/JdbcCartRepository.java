package com.impati.commerce.cart.adapter.out.persistence;

import com.impati.commerce.cart.application.CartRepository;
import com.impati.commerce.cart.domain.CartModels.Cart;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 *
 * <p>복원은 빈 {@link Cart}를 만들고 라인을 {@code add}로 다시 넣는다. 도메인의 add가 같은 SKU를
 * 합치는 규칙을 그대로 타므로 별도 restore 팩토리가 필요하지 않다.
 */
@Repository
public class JdbcCartRepository implements CartRepository {
    private static final String SELECT_CART = "select member_id from carts where member_id = :member_id";
    private static final String INSERT_CART = """
            insert into carts (member_id)
            select :member_id
             where not exists (select 1 from carts where member_id = :member_id)
            """;
    private static final String SELECT_LINES = """
            select sku_id, quantity
              from cart_lines
             where member_id = :member_id
             order by line_no
            """;
    private static final String DELETE_LINES = "delete from cart_lines where member_id = :member_id";
    private static final String INSERT_LINE = """
            insert into cart_lines (member_id, sku_id, line_no, quantity)
            values (:member_id, :sku_id, :line_no, :quantity)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCartRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findByMemberId(String memberId) {
        var params = new MapSqlParameterSource("member_id", memberId);
        var exists = !jdbc.queryForList(SELECT_CART, params, String.class).isEmpty();
        if (!exists) {
            return Optional.empty();
        }
        var cart = new Cart(memberId);
        jdbc.query(SELECT_LINES, params, rs -> {
            cart.add(rs.getString("sku_id"), rs.getInt("quantity"));
        });
        return Optional.of(cart);
    }

    @Override
    @Transactional
    public void save(Cart cart) {
        var params = new MapSqlParameterSource("member_id", cart.memberId());
        jdbc.update(INSERT_CART, params);
        jdbc.update(DELETE_LINES, params);

        var lines = cart.lines();
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            jdbc.update(INSERT_LINE, new MapSqlParameterSource()
                    .addValue("member_id", cart.memberId())
                    .addValue("sku_id", line.skuId())
                    .addValue("line_no", index)
                    .addValue("quantity", line.quantity()));
        }
    }
}
