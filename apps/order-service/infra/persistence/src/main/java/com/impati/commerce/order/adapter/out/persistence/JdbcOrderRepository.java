package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.port.in.OrderCursor;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.OrderWriter;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 이름 바인딩과 명시적인 컬럼만 쓴다. 주문과 라인은 같은 읽기 트랜잭션에서 복원한다. */
@Repository
public class JdbcOrderRepository implements OrderRepository, OrderWriter {
    private static final String ORDER_COLUMNS = """
            id, member_id, status, payment_id, shipment_id, inventory_reservation_id,
            ship_address_id, ship_alias, ship_recipient, ship_phone,
            ship_line1, ship_city, ship_postal_code, ship_default_address, created_at
            """;
    private static final String UPDATE_ORDER = """
            update orders
               set member_id = :member_id, status = :status, payment_id = :payment_id,
                   shipment_id = :shipment_id, inventory_reservation_id = :inventory_reservation_id,
                   ship_address_id = :ship_address_id, ship_alias = :ship_alias,
                   ship_recipient = :ship_recipient, ship_phone = :ship_phone,
                   ship_line1 = :ship_line1, ship_city = :ship_city,
                   ship_postal_code = :ship_postal_code, ship_default_address = :ship_default_address
             where id = :id
            """;
    private static final String INSERT_ORDER = """
            insert into orders (
                id, member_id, status, payment_id, shipment_id, inventory_reservation_id,
                ship_address_id, ship_alias, ship_recipient, ship_phone,
                ship_line1, ship_city, ship_postal_code, ship_default_address, created_at
            ) values (
                :id, :member_id, :status, :payment_id, :shipment_id, :inventory_reservation_id,
                :ship_address_id, :ship_alias, :ship_recipient, :ship_phone,
                :ship_line1, :ship_city, :ship_postal_code, :ship_default_address, :created_at
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
    private static final String SELECT_PAGE = "select " + ORDER_COLUMNS + " from orders where member_id = :member_id";
    private static final String CURSOR_PREDICATE = """
             and (created_at < :cursor_created_at
                  or (created_at = :cursor_created_at and id < :cursor_id))
            """;
    private static final String PAGE_ORDER = " order by created_at desc, id desc limit :limit";
    private static final String SELECT_LINES_BY_ORDERS = """
            select order_id, sku_id, product_id, product_name, sku_name, quantity, unit_amount, unit_currency
              from order_lines where order_id in (:order_ids) order by order_id, line_no
            """;
    private static final RowMapper<OrderRow> ORDER_ROW_MAPPER = (rs, rowNum) -> new OrderRow(
            rs.getString("id"), rs.getString("member_id"),
            new Address(rs.getString("ship_address_id"), rs.getString("ship_alias"),
                    rs.getString("ship_recipient"), rs.getString("ship_phone"), rs.getString("ship_line1"),
                    rs.getString("ship_city"), rs.getString("ship_postal_code"), rs.getBoolean("ship_default_address")),
            rs.getString("status"), rs.getString("payment_id"), rs.getString("shipment_id"),
            rs.getString("inventory_reservation_id"), rs.getObject("created_at", LocalDateTime.class));

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcOrderRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 생성 시각은 갱신하지 않는다. 행과 라인의 원자적인 저장은 OrderChanges가 담당한다. */
    @Override
    public void save(Order order) {
        var params = orderParams(order);
        if (jdbc.update(UPDATE_ORDER, params) == 0) jdbc.update(INSERT_ORDER, params);
        jdbc.update("delete from order_lines where order_id = :order_id", new MapSqlParameterSource("order_id", order.id()));
        var lines = order.lines();
        for (var index = 0; index < lines.size(); index++) {
            jdbc.update(INSERT_LINE, lineParams(order.id(), index, lines.get(index)));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(String orderId) {
        return restore(jdbc.query(SELECT_ORDER, new MapSqlParameterSource("id", orderId), ORDER_ROW_MAPPER))
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdAndMemberId(String orderId, String memberId) {
        return restore(jdbc.query(SELECT_ORDER + " and member_id = :member_id",
                new MapSqlParameterSource("id", orderId).addValue("member_id", memberId), ORDER_ROW_MAPPER))
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findBy(String memberId, OrderCursor cursor, int size) {
        var params = new MapSqlParameterSource("member_id", memberId).addValue("limit", size);
        var sql = SELECT_PAGE;
        if (cursor != null) {
            sql += CURSOR_PREDICATE;
            params.addValue("cursor_created_at", cursor.createdAt()).addValue("cursor_id", cursor.orderId());
        }
        return restore(jdbc.query(sql + PAGE_ORDER, params, ORDER_ROW_MAPPER));
    }

    /** 목록에서도 주문당 추가 SELECT를 하지 않고 라인을 한 번에 읽는다. 빈 IN 절은 만들지 않는다. */
    private List<Order> restore(List<OrderRow> rows) {
        if (rows.isEmpty()) return List.of();
        var lines = findLines(rows.stream().map(OrderRow::id).toList());
        return rows.stream().map(row -> row.toOrder(lines.getOrDefault(row.id(), List.of()), clock)).toList();
    }

    private Map<String, List<OrderLine>> findLines(List<String> orderIds) {
        var entities = jdbc.query(SELECT_LINES_BY_ORDERS, new MapSqlParameterSource("order_ids", orderIds),
                (rs, rowNum) -> new OrderLineRow(rs.getString("order_id"),
                        new OrderLine(rs.getString("sku_id"), rs.getString("product_id"),
                                rs.getString("product_name"), rs.getString("sku_name"), rs.getInt("quantity"),
                                new Money(rs.getLong("unit_amount"), rs.getString("unit_currency")))));
        return entities.stream().collect(Collectors.groupingBy(OrderLineRow::orderId,
                Collectors.mapping(OrderLineRow::line, Collectors.toList())));
    }

    private MapSqlParameterSource orderParams(Order order) {
        var address = order.shippingAddress();
        return new MapSqlParameterSource()
                .addValue("id", order.id()).addValue("member_id", order.memberId()).addValue("status", order.status())
                .addValue("payment_id", order.paymentId()).addValue("shipment_id", order.shipmentId())
                .addValue("inventory_reservation_id", order.inventoryReservationId())
                .addValue("ship_address_id", address.id()).addValue("ship_alias", address.alias())
                .addValue("ship_recipient", address.recipient()).addValue("ship_phone", address.phone())
                .addValue("ship_line1", address.line1()).addValue("ship_city", address.city())
                .addValue("ship_postal_code", address.postalCode()).addValue("ship_default_address", address.defaultAddress())
                .addValue("created_at", order.createdAt());
    }

    private MapSqlParameterSource lineParams(String orderId, int lineNo, OrderLine line) {
        return new MapSqlParameterSource("order_id", orderId).addValue("line_no", lineNo)
                .addValue("sku_id", line.skuId()).addValue("product_id", line.productId())
                .addValue("product_name", line.productName()).addValue("sku_name", line.skuName())
                .addValue("quantity", line.quantity()).addValue("unit_amount", line.unitPrice().amount())
                .addValue("unit_currency", line.unitPrice().currency());
    }

    private record OrderLineRow(String orderId, OrderLine line) { }

    private record OrderRow(String id, String memberId, Address address, String status, String paymentId,
            String shipmentId, String reservationId, LocalDateTime createdAt) {
        Order toOrder(List<OrderLine> lines, Clock clock) {
            return Order.restore(id, memberId, lines, address, status, paymentId, shipmentId, reservationId, createdAt, clock);
        }
    }
}
