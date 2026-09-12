package com.impati.commerce.shipping.adapter.out.persistence;

import com.impati.commerce.shipping.application.port.out.ShipmentRepository;
import com.impati.commerce.shipping.domain.ShippingModels.Address;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 */
@Repository
public class JdbcShipmentRepository implements ShipmentRepository {
    private static final String COLUMNS = """
            id, order_id, member_id, status, tracking_number,
            ship_address_id, ship_alias, ship_recipient, ship_phone,
            ship_line1, ship_city, ship_postal_code, ship_default_address
            """;

    private static final String INSERT = """
            insert into shipments (
                id, order_id, member_id, status, tracking_number,
                ship_address_id, ship_alias, ship_recipient, ship_phone,
                ship_line1, ship_city, ship_postal_code, ship_default_address
            ) values (
                :id, :order_id, :member_id, :status, :tracking_number,
                :ship_address_id, :ship_alias, :ship_recipient, :ship_phone,
                :ship_line1, :ship_city, :ship_postal_code, :ship_default_address
            )
            """;

    private static final String UPDATE = """
            update shipments
               set order_id = :order_id,
                   member_id = :member_id,
                   status = :status,
                   tracking_number = :tracking_number,
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

    private static final String SELECT_BY_ID = "select " + COLUMNS + " from shipments where id = :id";
    private static final String SELECT_BY_ORDER = "select " + COLUMNS + " from shipments where order_id = :order_id";
    private static final String SELECT_ALL = "select " + COLUMNS + " from shipments";

    private static final RowMapper<Shipment> ROW_MAPPER = (rs, rowNum) -> Shipment.restore(
            rs.getString("id"),
            rs.getString("order_id"),
            rs.getString("member_id"),
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
            rs.getString("tracking_number"),
            rs.getString("status")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShipmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(Shipment shipment) {
        try {
            jdbc.update(INSERT, params(shipment));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    @Transactional
    public void save(Shipment shipment) {
        var params = params(shipment);
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> findById(String shipmentId) {
        return jdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", shipmentId), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> findByOrderId(String orderId) {
        return jdbc.query(SELECT_BY_ORDER, new MapSqlParameterSource("order_id", orderId), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Shipment> findAll() {
        return jdbc.query(SELECT_ALL, ROW_MAPPER);
    }

    private static MapSqlParameterSource params(Shipment shipment) {
        var address = shipment.address();
        return new MapSqlParameterSource()
                .addValue("id", shipment.id())
                .addValue("order_id", shipment.orderId())
                .addValue("member_id", shipment.memberId())
                .addValue("status", shipment.status())
                .addValue("tracking_number", shipment.trackingNumber())
                .addValue("ship_address_id", address.id())
                .addValue("ship_alias", address.alias())
                .addValue("ship_recipient", address.recipient())
                .addValue("ship_phone", address.phone())
                .addValue("ship_line1", address.line1())
                .addValue("ship_city", address.city())
                .addValue("ship_postal_code", address.postalCode())
                .addValue("ship_default_address", address.defaultAddress());
    }
}
