package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrderRepository implements OrderRepository {
    private static final String UPDATE_ORDER = """
            update orders
               set member_id = ?, status = ?, payment_id = ?, shipment_id = ?, inventory_reservation_id = ?,
                   ship_address_id = ?, ship_alias = ?, ship_recipient = ?, ship_phone = ?,
                   ship_line1 = ?, ship_city = ?, ship_postal_code = ?, ship_default_address = ?
             where id = ?
            """;

    private static final String INSERT_ORDER = """
            insert into orders (
                id, member_id, status, payment_id, shipment_id, inventory_reservation_id,
                ship_address_id, ship_alias, ship_recipient, ship_phone,
                ship_line1, ship_city, ship_postal_code, ship_default_address
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_LINE = """
            insert into order_lines (
                order_id, line_no, sku_id, product_id, product_name, sku_name,
                quantity, unit_amount, unit_currency
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_ORDER = "select * from orders where id = ?";
    private static final String SELECT_LINES = "select * from order_lines where order_id = ? order by line_no";

    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        var address = order.shippingAddress();
        var updated = jdbc.update(
                UPDATE_ORDER,
                order.memberId(),
                order.status(),
                order.paymentId(),
                order.shipmentId(),
                order.inventoryReservationId(),
                address.id(),
                address.alias(),
                address.recipient(),
                address.phone(),
                address.line1(),
                address.city(),
                address.postalCode(),
                address.defaultAddress(),
                order.id()
        );
        if (updated == 0) {
            jdbc.update(
                    INSERT_ORDER,
                    order.id(),
                    order.memberId(),
                    order.status(),
                    order.paymentId(),
                    order.shipmentId(),
                    order.inventoryReservationId(),
                    address.id(),
                    address.alias(),
                    address.recipient(),
                    address.phone(),
                    address.line1(),
                    address.city(),
                    address.postalCode(),
                    address.defaultAddress()
            );
        }

        jdbc.update("delete from order_lines where order_id = ?", order.id());
        var lines = order.lines();
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            jdbc.update(
                    INSERT_LINE,
                    order.id(),
                    index,
                    line.skuId(),
                    line.productId(),
                    line.productName(),
                    line.skuName(),
                    line.quantity(),
                    line.unitPrice().amount(),
                    line.unitPrice().currency()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(String orderId) {
        var rows = jdbc.query(SELECT_ORDER, orderRowMapper(orderId), orderId);
        return rows.stream().findFirst();
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
                rs.getString("inventory_reservation_id")
        );
    }

    private List<OrderLine> findLines(String orderId) {
        return jdbc.query(
                SELECT_LINES,
                (rs, rowNum) -> new OrderLine(
                        rs.getString("sku_id"),
                        rs.getString("product_id"),
                        rs.getString("product_name"),
                        rs.getString("sku_name"),
                        rs.getInt("quantity"),
                        new Money(rs.getLong("unit_amount"), rs.getString("unit_currency"))
                ),
                orderId
        );
    }
}
