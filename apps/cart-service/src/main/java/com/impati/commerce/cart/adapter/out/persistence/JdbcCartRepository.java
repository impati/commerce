package com.impati.commerce.cart.adapter.out.persistence;

import com.impati.commerce.cart.application.port.out.CartRepository;
import com.impati.commerce.cart.domain.CartModels.Cart;
import com.impati.commerce.common.DomainException;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 *
 * <p>복원은 저장된 버전과 라인을 그대로 읽는다. 저장은 읽었던 버전과 비교해 오래된 수정이
 * 최신 장바구니나 이미 분리된 구매분을 덮어쓰지 못하게 한다.
 */
@Repository
public class JdbcCartRepository implements CartRepository {

    private static final String SELECT_CART = "select member_id, version from carts where member_id = :member_id";
    private static final String LOCK_CART = SELECT_CART + " for update";
    private static final String INSERT_CART = """
            insert into carts (member_id, version)
            select :member_id, :version
             where not exists (select 1 from carts where member_id = :member_id)
            """;
    private static final String UPDATE_CART_IF_UNCHANGED = """
            update carts
               set version = :version
             where member_id = :member_id
               and version = :expected_version
            """;
    private static final String UPDATE_CART_VERSION =
            "update carts set version = :version where member_id = :member_id";
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
    private static final String SELECT_SNAPSHOT = """
            select order_id, member_id, cart_version
              from cart_checkout_snapshots
             where order_id = :order_id
            """;
    private static final String SELECT_SNAPSHOT_LINES = """
            select sku_id, quantity
              from cart_checkout_snapshot_lines
             where order_id = :order_id
             order by line_no
            """;
    private static final String INSERT_SNAPSHOT = """
            insert into cart_checkout_snapshots (order_id, member_id, cart_version)
            values (:order_id, :member_id, :cart_version)
            """;
    private static final String INSERT_SNAPSHOT_LINE = """
            insert into cart_checkout_snapshot_lines (order_id, sku_id, line_no, quantity)
            values (:order_id, :sku_id, :line_no, :quantity)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCartRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 헤더와 라인을 같은 스냅샷으로 읽는다. 쓰기도 버전과 라인을 한 트랜잭션으로 갱신한다.
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Cart> findByMemberId(String memberId) {
        var params = new MapSqlParameterSource("member_id", memberId);
        var headers = jdbc.query(SELECT_CART, params,
                (rs, rowNum) -> Cart.restore(rs.getString("member_id"), rs.getLong("version")));
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        var cart = headers.getFirst();
        jdbc.query(SELECT_LINES, params, rs -> {
            cart.restoreLine(rs.getString("sku_id"), rs.getInt("quantity"));
        });
        return Optional.of(cart);
    }

    @Override
    @Transactional
    public void save(Cart cart) {
        var params = new MapSqlParameterSource()
                .addValue("member_id", cart.memberId())
                .addValue("version", cart.version());
        if (cart.isNew()) {
            insertCart(params);
        } else {
            updateCartIfUnchanged(params, cart.persistedVersion());
        }
        replaceLines(cart, params);
        markPersistedAfterCommit(cart);
    }

    private void insertCart(MapSqlParameterSource params) {
        if (jdbc.update(INSERT_CART, params) != 1) {
            throw DomainException.cartChanged("cart was created by another request");
        }
    }

    private void updateCartIfUnchanged(MapSqlParameterSource params, long expectedVersion) {
        var changed = jdbc.update(UPDATE_CART_IF_UNCHANGED,
                params.addValue("expected_version", expectedVersion));
        if (changed != 1) {
            throw DomainException.cartChanged("cart changed during modification");
        }
    }

    private void replaceLines(Cart cart, MapSqlParameterSource params) {
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

    private void markPersistedAfterCommit(Cart cart) {
        // 라인 저장이나 최종 커밋이 실패하면 객체의 기준 버전도 갱신하지 않는다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cart.markPersisted();
            }
        });
    }

    /**
     * 장바구니 행 잠금 안에서 버전 확인, 사본 저장과 현재 라인 삭제를 한 번에 수행한다.
     */
    @Override
    @Transactional
    public Cart checkout(String memberId, String orderId, long expectedVersion) {
        var memberParams = new MapSqlParameterSource("member_id", memberId);
        var locked = jdbc.query(LOCK_CART, memberParams, (rs, rowNum) -> Cart.restore(rs.getString("member_id"), rs.getLong("version")));
        if (locked.isEmpty()) {
            throw DomainException.cartEmpty("cart is empty");
        }

        var snapshotParams = new MapSqlParameterSource("order_id", orderId);
        var snapshots = jdbc.query(SELECT_SNAPSHOT, snapshotParams, (rs, rowNum) -> {
            if (!memberId.equals(rs.getString("member_id"))) {
                throw DomainException.conflict("checkout snapshot belongs to another member");
            }
            return Cart.restore(memberId, rs.getLong("cart_version"));
        });
        if (!snapshots.isEmpty()) {
            var snapshot = snapshots.getFirst();
            jdbc.query(SELECT_SNAPSHOT_LINES, snapshotParams,
                            (rs, rowNum) -> Map.entry(rs.getString("sku_id"), rs.getInt("quantity")))
                    .forEach(line -> snapshot.restoreLine(line.getKey(), line.getValue()));
            return snapshot;
        }

        var cart = locked.getFirst();
        if (cart.version() != expectedVersion) {
            throw DomainException.cartChanged("cart changed after checkout started");
        }
        jdbc.query(SELECT_LINES, memberParams, (rs, rowNum) -> Map.entry(rs.getString("sku_id"), rs.getInt("quantity")))
                .forEach(line -> cart.restoreLine(line.getKey(), line.getValue()));
        if (cart.lines().isEmpty()) {
            throw DomainException.cartEmpty("cart is empty");
        }

        jdbc.update(INSERT_SNAPSHOT, new MapSqlParameterSource()
                .addValue("order_id", orderId)
                .addValue("member_id", memberId)
                .addValue("cart_version", expectedVersion));
        for (var index = 0; index < cart.lines().size(); index++) {
            var line = cart.lines().get(index);
            jdbc.update(INSERT_SNAPSHOT_LINE, new MapSqlParameterSource()
                    .addValue("order_id", orderId)
                    .addValue("sku_id", line.skuId())
                    .addValue("line_no", index)
                    .addValue("quantity", line.quantity()));
        }
        jdbc.update(DELETE_LINES, memberParams);
        jdbc.update(UPDATE_CART_VERSION, new MapSqlParameterSource()
                .addValue("member_id", memberId)
                .addValue("version", expectedVersion + 1));
        return cart;
    }
}
