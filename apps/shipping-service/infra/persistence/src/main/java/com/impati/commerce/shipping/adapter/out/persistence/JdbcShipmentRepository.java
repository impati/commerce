package com.impati.commerce.shipping.adapter.out.persistence;

import com.impati.commerce.shipping.application.port.out.ShipmentRepository;
import com.impati.commerce.shipping.application.port.out.CarrierEventRecord;
import com.impati.commerce.shipping.domain.ShippingModels.Address;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 */
@Repository
public class JdbcShipmentRepository implements ShipmentRepository {
    private static final String COLUMNS = """
            id, order_id, member_id, status, registration_status, cancellation_status,
            carrier_code, carrier_name, tracking_number, last_carrier_event_at,
            ship_address_id, ship_alias, ship_recipient, ship_phone,
            ship_line1, ship_city, ship_postal_code, ship_default_address
            """;

    private static final String INSERT = """
            insert into shipments (
                id, order_id, member_id, status, registration_status, cancellation_status,
                carrier_code, carrier_name, tracking_number, last_carrier_event_at,
                ship_address_id, ship_alias, ship_recipient, ship_phone,
                ship_line1, ship_city, ship_postal_code, ship_default_address
            ) values (
                :id, :order_id, :member_id, :status, :registration_status, :cancellation_status,
                :carrier_code, :carrier_name, :tracking_number, :last_carrier_event_at,
                :ship_address_id, :ship_alias, :ship_recipient, :ship_phone,
                :ship_line1, :ship_city, :ship_postal_code, :ship_default_address
            )
            """;

    private static final String UPDATE = """
            update shipments
               set order_id = :order_id,
                   member_id = :member_id,
                   status = :status,
                   registration_status = :registration_status,
                   cancellation_status = :cancellation_status,
                   carrier_code = :carrier_code,
                   carrier_name = :carrier_name,
                   tracking_number = :tracking_number,
                   last_carrier_event_at = :last_carrier_event_at,
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
    private static final String SELECT_BY_ID_FOR_UPDATE = SELECT_BY_ID + " for update";
    private static final String SELECT_BY_ORDER = "select " + COLUMNS + " from shipments where order_id = :order_id";
    private static final String SELECT_BY_CARRIER_TRACKING_FOR_UPDATE = "select " + COLUMNS
            + " from shipments where carrier_code = :carrier_code and tracking_number = :tracking_number for update";
    private static final String SELECT_ALL = "select " + COLUMNS + " from shipments";

    private static final String INSERT_CARRIER_EVENT = """
            insert ignore into shipment_carrier_events (
                event_id, shipment_id, carrier_code, tracking_number, event_type,
                occurred_at, received_at, last_received_at, processing_result, shipment_status, duplicate_count
            ) values (
                :event_id, :shipment_id, :carrier_code, :tracking_number, :event_type,
                :occurred_at, :received_at, :received_at, :processing_result, :shipment_status, 0
            )
            """;
    private static final String SELECT_CARRIER_EVENT = """
            select event_id, shipment_id, carrier_code, tracking_number, event_type,
                   occurred_at, received_at, last_received_at, processing_result, shipment_status, duplicate_count
              from shipment_carrier_events where event_id = :event_id
            """;

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
            rs.getString("status"),
            rs.getString("registration_status"),
            rs.getString("cancellation_status"),
            rs.getString("carrier_code"),
            rs.getString("carrier_name"),
            rs.getString("tracking_number"),
            offset(rs, "last_carrier_event_at")
    );

    private static final RowMapper<CarrierEventRecord> EVENT_ROW_MAPPER = (rs, rowNum) -> new CarrierEventRecord(
            rs.getString("event_id"),
            rs.getString("shipment_id"),
            rs.getString("carrier_code"),
            rs.getString("tracking_number"),
            rs.getString("event_type"),
            offset(rs, "occurred_at"),
            offset(rs, "received_at"),
            offset(rs, "last_received_at"),
            rs.getString("processing_result"),
            rs.getString("shipment_status"),
            rs.getInt("duplicate_count")
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
    @Transactional
    public Optional<Shipment> findByIdForUpdate(String shipmentId) {
        return jdbc.query(SELECT_BY_ID_FOR_UPDATE, new MapSqlParameterSource("id", shipmentId), ROW_MAPPER)
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
    @Transactional
    public Optional<Shipment> findByCarrierAndTrackingForUpdate(String carrierCode, String trackingNumber) {
        var params = new MapSqlParameterSource()
                .addValue("carrier_code", carrierCode)
                .addValue("tracking_number", trackingNumber);
        return jdbc.query(SELECT_BY_CARRIER_TRACKING_FOR_UPDATE, params, ROW_MAPPER).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Shipment> findAll() {
        return jdbc.query(SELECT_ALL, ROW_MAPPER);
    }

    @Override
    public boolean insertCarrierEventIfAbsent(CarrierEventRecord event) {
        var params = new MapSqlParameterSource()
                .addValue("event_id", event.eventId())
                .addValue("shipment_id", event.shipmentId())
                .addValue("carrier_code", event.carrierCode())
                .addValue("tracking_number", event.trackingNumber())
                .addValue("event_type", event.eventType())
                .addValue("occurred_at", event.occurredAt())
                .addValue("received_at", event.receivedAt())
                .addValue("processing_result", event.processingResult())
                .addValue("shipment_status", event.shipmentStatus());
        return jdbc.update(INSERT_CARRIER_EVENT, params) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CarrierEventRecord> findCarrierEvent(String eventId) {
        return jdbc.query(SELECT_CARRIER_EVENT, new MapSqlParameterSource("event_id", eventId), EVENT_ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public void completeCarrierEvent(String eventId, String result, String shipmentStatus) {
        jdbc.update("""
                update shipment_carrier_events
                   set processing_result = :processing_result, shipment_status = :shipment_status
                 where event_id = :event_id
                """, new MapSqlParameterSource()
                .addValue("event_id", eventId)
                .addValue("processing_result", result)
                .addValue("shipment_status", shipmentStatus));
    }

    @Override
    public void recordDuplicateCarrierEvent(String eventId, OffsetDateTime receivedAt) {
        jdbc.update("""
                update shipment_carrier_events
                   set duplicate_count = duplicate_count + 1, last_received_at = :last_received_at
                 where event_id = :event_id
                """, new MapSqlParameterSource()
                .addValue("event_id", eventId)
                .addValue("last_received_at", receivedAt));
    }

    private static MapSqlParameterSource params(Shipment shipment) {
        var address = shipment.address();
        return new MapSqlParameterSource()
                .addValue("id", shipment.id())
                .addValue("order_id", shipment.orderId())
                .addValue("member_id", shipment.memberId())
                .addValue("status", shipment.status().name())
                .addValue("registration_status", shipment.registrationStatus().name())
                .addValue("cancellation_status", shipment.cancellationStatus().name())
                .addValue("carrier_code", shipment.carrierCode())
                .addValue("carrier_name", shipment.carrierName())
                .addValue("tracking_number", shipment.trackingNumber())
                .addValue("last_carrier_event_at", shipment.lastCarrierEventAt())
                .addValue("ship_address_id", address.id())
                .addValue("ship_alias", address.alias())
                .addValue("ship_recipient", address.recipient())
                .addValue("ship_phone", address.phone())
                .addValue("ship_line1", address.line1())
                .addValue("ship_city", address.city())
                .addValue("ship_postal_code", address.postalCode())
                .addValue("ship_default_address", address.defaultAddress());
    }

    private static OffsetDateTime offset(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }
}
