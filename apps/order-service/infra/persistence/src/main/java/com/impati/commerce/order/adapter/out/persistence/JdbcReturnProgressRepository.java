package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.port.out.ReturnProgressRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.ReturnProgress;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcReturnProgressRepository implements ReturnProgressRepository {
    private static final String COLUMNS = """
            id, order_id, member_id, reason, description, aware_date, refund_amount, refund_currency,
            status, refund_status, inventory_status, return_shipment_id, disposition, item_condition,
            pickup_address_id, pickup_alias, pickup_recipient, pickup_phone, pickup_line1, pickup_city,
            pickup_postal_code, pickup_default_address, created_at, received_at, inspection_due_at
            """;
    private static final RowMapper<ReturnProgress> MAPPER = (rs, rowNum) -> ReturnProgress.restore(
            rs.getString("id"), rs.getString("order_id"), rs.getString("member_id"), rs.getString("reason"),
            rs.getString("description"), rs.getObject("aware_date", java.time.LocalDate.class),
            new Money(rs.getLong("refund_amount"), rs.getString("refund_currency")),
            new Address(rs.getString("pickup_address_id"), rs.getString("pickup_alias"),
                    rs.getString("pickup_recipient"), rs.getString("pickup_phone"), rs.getString("pickup_line1"),
                    rs.getString("pickup_city"), rs.getString("pickup_postal_code"),
                    rs.getBoolean("pickup_default_address")),
            rs.getString("status"), rs.getString("refund_status"), rs.getString("inventory_status"),
            rs.getString("return_shipment_id"), rs.getString("disposition"), rs.getString("item_condition"),
            offset(rs, "created_at"), offset(rs, "received_at"), offset(rs, "inspection_due_at"));

    private final NamedParameterJdbcTemplate jdbc;
    public JdbcReturnProgressRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public boolean insertIfAbsent(ReturnProgress progress) {
        try {
            jdbc.update("insert into return_progress (" + COLUMNS + ") values ("
                    + named(COLUMNS) + ")", params(progress));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    @Transactional
    public void save(ReturnProgress progress) {
        jdbc.update("""
                update return_progress set
                    pickup_address_id=:pickup_address_id, pickup_alias=:pickup_alias,
                    pickup_recipient=:pickup_recipient, pickup_phone=:pickup_phone,
                    pickup_line1=:pickup_line1, pickup_city=:pickup_city,
                    pickup_postal_code=:pickup_postal_code, pickup_default_address=:pickup_default_address,
                    status=:status, refund_status=:refund_status, inventory_status=:inventory_status,
                    return_shipment_id=:return_shipment_id, disposition=:disposition,
                    item_condition=:item_condition, received_at=:received_at,
                    inspection_due_at=:inspection_due_at
                 where id=:id
                """, params(progress));
    }

    @Override public Optional<ReturnProgress> findById(String id) { return one("id = :id", "id", id, false); }
    @Override public Optional<ReturnProgress> findByIdForUpdate(String id) { return one("id = :id", "id", id, true); }
    @Override public Optional<ReturnProgress> findByOrderId(String orderId) {
        return jdbc.query("select " + COLUMNS + " from return_progress where order_id=:order_id "
                        + "order by created_at desc limit 1",
                new MapSqlParameterSource("order_id", orderId), MAPPER).stream().findFirst();
    }

    @Override
    public List<ReturnProgress> findRecoverable(OffsetDateTime now, int limit) {
        return jdbc.query("select " + COLUMNS + " from return_progress where "
                        + "status in ('REQUESTED','RESCHEDULE_PENDING','WITHDRAWAL_PENDING') "
                        + "or refund_status='PENDING' or inventory_status='PENDING' "
                        + "or (status='RECEIVED' and inventory_status='NOT_READY' and inspection_due_at <= :now) "
                        + "order by created_at limit :limit",
                new MapSqlParameterSource("now", now).addValue("limit", limit), MAPPER);
    }

    private Optional<ReturnProgress> one(String predicate, String key, String value, boolean lock) {
        return jdbc.query("select " + COLUMNS + " from return_progress where " + predicate
                        + (lock ? " for update" : ""),
                new MapSqlParameterSource(key, value), MAPPER).stream().findFirst();
    }

    private static MapSqlParameterSource params(ReturnProgress p) {
        var a = p.pickupAddress();
        return new MapSqlParameterSource().addValue("id", p.id()).addValue("order_id", p.orderId())
                .addValue("member_id", p.memberId()).addValue("reason", p.reason())
                .addValue("description", p.description()).addValue("aware_date", p.awareDate())
                .addValue("refund_amount", p.refundAmount().amount()).addValue("refund_currency", p.refundAmount().currency())
                .addValue("status", p.status().name()).addValue("refund_status", p.refundStatus().name())
                .addValue("inventory_status", p.inventoryStatus().name()).addValue("return_shipment_id", p.returnShipmentId())
                .addValue("disposition", p.disposition()).addValue("item_condition", p.condition())
                .addValue("pickup_address_id", a.id()).addValue("pickup_alias", a.alias())
                .addValue("pickup_recipient", a.recipient()).addValue("pickup_phone", a.phone())
                .addValue("pickup_line1", a.line1()).addValue("pickup_city", a.city())
                .addValue("pickup_postal_code", a.postalCode()).addValue("pickup_default_address", a.defaultAddress())
                .addValue("created_at", p.createdAt()).addValue("received_at", p.receivedAt())
                .addValue("inspection_due_at", p.inspectionDueAt());
    }

    private static String named(String columns) {
        return columns.replaceAll("\\s+", "").replace(",", ",:").replaceFirst("^", ":");
    }
    private static OffsetDateTime offset(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
