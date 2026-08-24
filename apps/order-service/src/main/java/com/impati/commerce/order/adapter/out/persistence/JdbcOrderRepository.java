package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 컴파일러도
 * DB도 잡지 못하고, 쓰기와 읽기가 같은 방향으로 틀리면 왕복 테스트조차 통과한다. 컬럼을 추가할 때
 * 값이 밀리는 사고가 구조적으로 생기지 않게 한다.
 *
 * <p>{@code select *}도 쓰지 않는다. 컬럼이 추가되면 결과셋 모양이 말없이 바뀐다.
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {
    private static final String ORDER_COLUMNS = """
            id, member_id, status, payment_id, shipment_id, inventory_reservation_id,
            payment_outcome_unknown,
            ship_address_id, ship_alias, ship_recipient, ship_phone,
            ship_line1, ship_city, ship_postal_code, ship_default_address
            """;

    private static final String LINE_COLUMNS = """
            sku_id, product_id, product_name, sku_name, quantity, unit_amount, unit_currency
            """;

    private static final String UPDATE_ORDER = """
            update orders
               set member_id = :member_id,
                   status = :status,
                   payment_id = :payment_id,
                   shipment_id = :shipment_id,
                   inventory_reservation_id = :inventory_reservation_id,
                   payment_outcome_unknown = :payment_outcome_unknown,
                   payment_reconcile_after = case
                       when :payment_outcome_unknown then payment_reconcile_after
                       else null
                   end,
                   ship_address_id = :ship_address_id,
                   ship_alias = :ship_alias,
                   ship_recipient = :ship_recipient,
                   ship_phone = :ship_phone,
                   ship_line1 = :ship_line1,
                   ship_city = :ship_city,
                   ship_postal_code = :ship_postal_code,
                   ship_default_address = :ship_default_address
             where id = :id
            """;

    private static final String INSERT_ORDER = """
            insert into orders (
                id, member_id, status, payment_id, shipment_id, inventory_reservation_id,
                payment_outcome_unknown,
                ship_address_id, ship_alias, ship_recipient, ship_phone,
                ship_line1, ship_city, ship_postal_code, ship_default_address
            ) values (
                :id, :member_id, :status, :payment_id, :shipment_id, :inventory_reservation_id,
                :payment_outcome_unknown,
                :ship_address_id, :ship_alias, :ship_recipient, :ship_phone,
                :ship_line1, :ship_city, :ship_postal_code, :ship_default_address
            )
            """;

    private static final String INSERT_LINE = """
            insert into order_lines (
                order_id, line_no, sku_id, product_id, product_name, sku_name,
                quantity, unit_amount, unit_currency
            ) values (
                :order_id, :line_no, :sku_id, :product_id, :product_name, :sku_name,
                :quantity, :unit_amount, :unit_currency
            )
            """;

    private static final String SELECT_ORDER = "select " + ORDER_COLUMNS + " from orders where id = :id";

    private static final String SELECT_RECONCILE_CANDIDATES = """
            select id
              from orders
             where payment_outcome_unknown = true
               and (payment_reconcile_after is null or payment_reconcile_after <= :now)
             order by payment_reconcile_after nulls first
             limit :batch_size
            """;

    private static final String CLAIM_FOR_RECONCILE = """
            update orders
               set payment_reconcile_after = :retry_after
             where id = :id
               and payment_outcome_unknown = true
               and (payment_reconcile_after is null or payment_reconcile_after <= :now)
            """;

    private static final String DELETE_LINES = "delete from order_lines where order_id = :order_id";
    private static final String SELECT_LINES =
            "select " + LINE_COLUMNS + " from order_lines where order_id = :order_id order by line_no";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcOrderRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * aggregate 전체를 덮어쓴다.
     *
     * <p>라인은 매번 지우고 다시 넣는다. 지금 라인은 생성 후 바뀌지 않지만, 나중에 바뀌게 됐을 때
     * 조용히 반영되지 않는 쪽보다 매번 쓰는 편이 안전하다. aggregate가 작아 비용도 작다.
     */
    @Override
    @Transactional
    public void save(Order order) {
        var params = orderParams(order);
        if (jdbc.update(UPDATE_ORDER, params) == 0) {
            jdbc.update(INSERT_ORDER, params);
        }

        jdbc.update(DELETE_LINES, new MapSqlParameterSource("order_id", order.id()));
        var lines = order.lines();
        for (var index = 0; index < lines.size(); index++) {
            jdbc.update(INSERT_LINE, lineParams(order.id(), index, lines.get(index)));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(String orderId) {
        var rows = jdbc.query(SELECT_ORDER, new MapSqlParameterSource("id", orderId), orderRowMapper(orderId));
        return rows.stream().findFirst();
    }

    private MapSqlParameterSource orderParams(Order order) {
        var address = order.shippingAddress();
        return new MapSqlParameterSource()
                .addValue("id", order.id())
                .addValue("member_id", order.memberId())
                .addValue("status", order.status())
                .addValue("payment_id", order.paymentId())
                .addValue("shipment_id", order.shipmentId())
                .addValue("inventory_reservation_id", order.inventoryReservationId())
                .addValue("payment_outcome_unknown", order.paymentOutcomeUnknown())
                .addValue("ship_address_id", address.id())
                .addValue("ship_alias", address.alias())
                .addValue("ship_recipient", address.recipient())
                .addValue("ship_phone", address.phone())
                .addValue("ship_line1", address.line1())
                .addValue("ship_city", address.city())
                .addValue("ship_postal_code", address.postalCode())
                .addValue("ship_default_address", address.defaultAddress());
    }

    private MapSqlParameterSource lineParams(String orderId, int lineNo, OrderLine line) {
        return new MapSqlParameterSource()
                .addValue("order_id", orderId)
                .addValue("line_no", lineNo)
                .addValue("sku_id", line.skuId())
                .addValue("product_id", line.productId())
                .addValue("product_name", line.productName())
                .addValue("sku_name", line.skuName())
                .addValue("quantity", line.quantity())
                .addValue("unit_amount", line.unitPrice().amount())
                .addValue("unit_currency", line.unitPrice().currency());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findPaymentReconciliationCandidates(int batchSize) {
        return jdbc.queryForList(
                SELECT_RECONCILE_CANDIDATES,
                new MapSqlParameterSource()
                        .addValue("now", now())
                        .addValue("batch_size", batchSize),
                String.class
        );
    }

    /**
     * 조건부 UPDATE의 갱신 행 수가 점유의 승자를 정한다 (ADR-0009).
     *
     * <p>{@code select for update}를 쓰지 않는 이유는 잠금이 트랜잭션 수명에 묶이기 때문이다.
     * 점유한 뒤에 결제 서비스를 부르는데, 잠금을 들고 부르면 외부 호출이 DB 트랜잭션을 늘리고
     * 호출 전에 트랜잭션을 닫으면 잠금이 아무것도 지켜주지 않는다. 조건부 UPDATE는 짧은
     * 트랜잭션 하나로 끝나고 그 뒤의 호출은 어떤 잠금도 잡지 않는다.
     *
     * <p>두 시각을 같은 {@code now}에서 만든다. 따로 읽으면 조건과 갱신값이 미세하게 어긋난다.
     */
    @Override
    @Transactional
    public Optional<Order> claimForPaymentReconciliation(String orderId, Duration retryDelay) {
        var now = now();
        var claimed = jdbc.update(CLAIM_FOR_RECONCILE, new MapSqlParameterSource()
                .addValue("id", orderId)
                .addValue("now", now)
                .addValue("retry_after", now.plus(retryDelay)));
        if (claimed == 0) {
            return Optional.empty();
        }
        return findById(orderId);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private RowMapper<Order> orderRowMapper(String orderId) {
        return (rs, rowNum) -> Order.restore(
                rs.getString("id"),
                rs.getString("member_id"),
                findLines(orderId),
                new Address(
                        rs.getString("ship_address_id"),
                        rs.getString("ship_alias"),
                        rs.getString("ship_recipient"),
                        rs.getString("ship_phone"),
                        rs.getString("ship_line1"),
                        rs.getString("ship_city"),
                        rs.getString("ship_postal_code"),
                        rs.getBoolean("ship_default_address")
                ),
                rs.getString("status"),
                rs.getString("payment_id"),
                rs.getString("shipment_id"),
                rs.getString("inventory_reservation_id"),
                rs.getBoolean("payment_outcome_unknown")
        );
    }

    private List<OrderLine> findLines(String orderId) {
        return jdbc.query(
                SELECT_LINES,
                new MapSqlParameterSource("order_id", orderId),
                (rs, rowNum) -> new OrderLine(
                        rs.getString("sku_id"),
                        rs.getString("product_id"),
                        rs.getString("product_name"),
                        rs.getString("sku_name"),
                        rs.getInt("quantity"),
                        new Money(rs.getLong("unit_amount"), rs.getString("unit_currency"))
                )
        );
    }
}
