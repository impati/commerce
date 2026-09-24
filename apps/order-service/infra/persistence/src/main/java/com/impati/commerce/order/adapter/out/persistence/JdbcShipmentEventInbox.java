package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.order.application.port.out.ShipmentEventInbox;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcShipmentEventInbox implements ShipmentEventInbox {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcShipmentEventInbox(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean recordIfAbsent(String eventId) {
        return jdbc.update("insert ignore into consumed_shipment_events (event_id, consumed_at) "
                        + "values (:event_id, :consumed_at)",
                new MapSqlParameterSource("event_id", eventId).addValue("consumed_at",
                        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) == 1;
    }
}
