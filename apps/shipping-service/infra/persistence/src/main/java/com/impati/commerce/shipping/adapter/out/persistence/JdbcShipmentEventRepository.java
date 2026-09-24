package com.impati.commerce.shipping.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.shipping.application.port.out.ShipmentEventRepository;
import com.impati.commerce.shipping.domain.ShipmentEvent;
import com.impati.commerce.shipping.domain.ShipmentEvent.PublishStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcShipmentEventRepository implements ShipmentEventRepository {
    private static final String COLUMNS = "id, type, shipment_id, order_id, member_id, occurred_at, "
            + "payload, publish_status, attempts, last_error";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JdbcShipmentEventRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void save(ShipmentEvent event) {
        jdbc.update("""
                insert into shipment_events
                    (id, type, shipment_id, order_id, member_id, occurred_at, payload,
                     publish_status, attempts, last_error)
                values (:id, :type, :shipment_id, :order_id, :member_id, :occurred_at, :payload,
                        :publish_status, :attempts, :last_error)
                """, params(event));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<ShipmentEvent> claimForPublish(String publishId, int shipmentBatchSize, Duration retryDelay) {
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var shipmentIds = jdbc.queryForList("""
                select shipment_id from shipment_events
                 where publish_status in ('PENDING', 'FAILED')
                 group by shipment_id
                having sum(case when publish_status = 'FAILED' then 1 else 0 end) = 0
                   and sum(case when publish_status = 'PENDING' then 1 else 0 end) > 0
                   and max(coalesce(next_attempt_after, :now)) <= :now
                 order by min(seq)
                 limit :limit
                """, new MapSqlParameterSource("now", now).addValue("limit", shipmentBatchSize), String.class);
        if (shipmentIds.isEmpty()) return List.of();

        jdbc.update("""
                update shipment_events
                   set claim_id = :claim_id, next_attempt_after = :retry_after
                 where shipment_id in (:shipment_ids) and publish_status = 'PENDING'
                   and (next_attempt_after is null or next_attempt_after <= :now)
                """, new MapSqlParameterSource("claim_id", publishId)
                .addValue("retry_after", now.plus(retryDelay))
                .addValue("shipment_ids", shipmentIds).addValue("now", now));

        var incomplete = Set.copyOf(jdbc.queryForList("""
                select shipment_id from shipment_events
                 where shipment_id in (:shipment_ids) and publish_status = 'PENDING'
                 group by shipment_id
                having sum(case when claim_id = :claim_id then 0 else 1 end) > 0
                """, new MapSqlParameterSource("shipment_ids", shipmentIds).addValue("claim_id", publishId),
                String.class));
        return jdbc.query("select " + COLUMNS
                        + " from shipment_events where claim_id = :claim_id and publish_status = 'PENDING'"
                        + " order by shipment_id, seq",
                new MapSqlParameterSource("claim_id", publishId), (rs, rowNum) -> restore(rs))
                .stream().filter(event -> !incomplete.contains(event.shipmentId())).toList();
    }

    @Override
    @Transactional
    public void savePublishResult(ShipmentEvent event) {
        jdbc.update("""
                update shipment_events set publish_status = :publish_status, attempts = :attempts,
                    last_error = :last_error where id = :id
                """, new MapSqlParameterSource("id", event.id())
                .addValue("publish_status", event.publishStatus().name())
                .addValue("attempts", event.attempts()).addValue("last_error", event.lastError()));
    }

    private MapSqlParameterSource params(ShipmentEvent event) {
        try {
            return new MapSqlParameterSource("id", event.id()).addValue("type", event.type())
                    .addValue("shipment_id", event.shipmentId()).addValue("order_id", event.orderId())
                    .addValue("member_id", event.memberId()).addValue("occurred_at", event.occurredAt())
                    .addValue("payload", objectMapper.writeValueAsString(event.payload()))
                    .addValue("publish_status", event.publishStatus().name())
                    .addValue("attempts", event.attempts()).addValue("last_error", event.lastError());
        } catch (Exception failure) {
            throw new IllegalStateException("shipment event payload could not be encoded", failure);
        }
    }

    private ShipmentEvent restore(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            Map<String, String> payload = objectMapper.readValue(rs.getString("payload"), new TypeReference<>() { });
            return ShipmentEvent.restore(rs.getString("id"), rs.getString("type"), rs.getString("shipment_id"),
                    rs.getString("order_id"), rs.getString("member_id"),
                    rs.getObject("occurred_at", OffsetDateTime.class), payload,
                    PublishStatus.valueOf(rs.getString("publish_status")), rs.getInt("attempts"),
                    rs.getString("last_error"));
        } catch (java.sql.SQLException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("shipment event payload could not be decoded", failure);
        }
    }
}
