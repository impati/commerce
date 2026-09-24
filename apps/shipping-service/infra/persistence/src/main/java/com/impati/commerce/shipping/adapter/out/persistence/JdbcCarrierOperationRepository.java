package com.impati.commerce.shipping.adapter.out.persistence;

import com.impati.commerce.shipping.application.port.out.CarrierOperationRepository;
import com.impati.commerce.shipping.domain.CarrierOperation;
import com.impati.commerce.shipping.domain.CarrierOperation.Status;
import com.impati.commerce.shipping.domain.CarrierOperation.Type;
import java.time.Clock;
import java.time.Duration;
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
public class JdbcCarrierOperationRepository implements CarrierOperationRepository {
    private static final String COLUMNS = "idempotency_key, shipment_id, operation_type, operation_status, "
            + "claim_generation, claim_until, next_attempt_at, attempts, last_error, created_at, updated_at";
    private static final String SELECT_BY_KEY = "select " + COLUMNS
            + " from carrier_operations where idempotency_key = :idempotency_key";
    private static final RowMapper<CarrierOperation> ROW_MAPPER = (rs, rowNum) -> new CarrierOperation(
            rs.getString("idempotency_key"),
            rs.getString("shipment_id"),
            Type.valueOf(rs.getString("operation_type")),
            Status.valueOf(rs.getString("operation_status")),
            rs.getLong("claim_generation"),
            rs.getObject("claim_until", OffsetDateTime.class),
            rs.getObject("next_attempt_at", OffsetDateTime.class),
            rs.getInt("attempts"),
            rs.getString("last_error"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcCarrierOperationRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(CarrierOperation operation) {
        try {
            jdbc.update("""
                    insert into carrier_operations (
                        idempotency_key, shipment_id, operation_type, operation_status,
                        claim_generation, claim_until, next_attempt_at, attempts, last_error,
                        created_at, updated_at
                    ) values (
                        :idempotency_key, :shipment_id, :operation_type, :operation_status,
                        :claim_generation, :claim_until, :next_attempt_at, :attempts, :last_error,
                        :created_at, :updated_at
                    )
                    """, params(operation));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CarrierOperation> find(String idempotencyKey) {
        return jdbc.query(SELECT_BY_KEY, key(idempotencyKey), ROW_MAPPER).stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<CarrierOperation> claim(String idempotencyKey, Duration leaseDuration) {
        var now = now();
        var updated = jdbc.update("""
                update carrier_operations
                   set claim_generation = claim_generation + 1,
                       claim_until = :claim_until,
                       attempts = attempts + 1,
                       updated_at = :now
                 where idempotency_key = :idempotency_key
                   and operation_status = 'PENDING'
                   and next_attempt_at <= :now
                   and (claim_until is null or claim_until <= :now)
                """, key(idempotencyKey).addValue("now", now)
                .addValue("claim_until", now.plus(leaseDuration)));
        return updated == 1 ? find(idempotencyKey) : Optional.empty();
    }

    @Override
    @Transactional
    public List<CarrierOperation> claimDue(int batchSize, Duration leaseDuration) {
        var now = now();
        var keys = jdbc.queryForList("""
                select idempotency_key
                  from carrier_operations
                 where operation_status = 'PENDING'
                   and next_attempt_at <= :now
                   and (claim_until is null or claim_until <= :now)
                 order by created_at, idempotency_key
                 limit :limit
                   for update skip locked
                """, new MapSqlParameterSource("now", now).addValue("limit", batchSize), String.class);
        if (keys.isEmpty()) {
            return List.of();
        }
        jdbc.update("""
                update carrier_operations
                   set claim_generation = claim_generation + 1,
                       claim_until = :claim_until,
                       attempts = attempts + 1,
                       updated_at = :now
                 where idempotency_key in (:keys)
                """, new MapSqlParameterSource("keys", keys).addValue("now", now)
                .addValue("claim_until", now.plus(leaseDuration)));
        return jdbc.query("select " + COLUMNS
                        + " from carrier_operations where idempotency_key in (:keys) order by created_at, idempotency_key",
                new MapSqlParameterSource("keys", keys), ROW_MAPPER);
    }

    @Override
    public boolean succeed(String idempotencyKey, long claimGeneration, OffsetDateTime completedAt) {
        return finish(idempotencyKey, claimGeneration, Status.SUCCEEDED, null, completedAt);
    }

    @Override
    public boolean retry(String idempotencyKey, long claimGeneration, String error, OffsetDateTime nextAttemptAt) {
        return jdbc.update("""
                update carrier_operations
                   set claim_until = null,
                       next_attempt_at = :next_attempt_at,
                       last_error = :last_error,
                       updated_at = :updated_at
                 where idempotency_key = :idempotency_key
                   and operation_status = 'PENDING'
                   and claim_generation = :claim_generation
                """, claim(idempotencyKey, claimGeneration)
                .addValue("next_attempt_at", nextAttemptAt)
                .addValue("last_error", truncate(error))
                .addValue("updated_at", now())) == 1;
    }

    @Override
    public boolean reject(String idempotencyKey, long claimGeneration, String error, OffsetDateTime completedAt) {
        return finish(idempotencyKey, claimGeneration, Status.REJECTED, error, completedAt);
    }

    @Override
    public boolean requireAttention(
            String idempotencyKey,
            long claimGeneration,
            String error,
            OffsetDateTime completedAt
    ) {
        return finish(idempotencyKey, claimGeneration, Status.ATTENTION_REQUIRED, error, completedAt);
    }

    private boolean finish(
            String idempotencyKey,
            long claimGeneration,
            Status status,
            String error,
            OffsetDateTime completedAt
    ) {
        return jdbc.update("""
                update carrier_operations
                   set operation_status = :operation_status,
                       claim_until = null,
                       last_error = :last_error,
                       updated_at = :updated_at
                 where idempotency_key = :idempotency_key
                   and operation_status = 'PENDING'
                   and claim_generation = :claim_generation
                """, claim(idempotencyKey, claimGeneration)
                .addValue("operation_status", status.name())
                .addValue("last_error", truncate(error))
                .addValue("updated_at", completedAt)) == 1;
    }

    private static MapSqlParameterSource params(CarrierOperation operation) {
        return key(operation.idempotencyKey())
                .addValue("shipment_id", operation.shipmentId())
                .addValue("operation_type", operation.type().name())
                .addValue("operation_status", operation.status().name())
                .addValue("claim_generation", operation.claimGeneration())
                .addValue("claim_until", operation.claimUntil())
                .addValue("next_attempt_at", operation.nextAttemptAt())
                .addValue("attempts", operation.attempts())
                .addValue("last_error", operation.lastError())
                .addValue("created_at", operation.createdAt())
                .addValue("updated_at", operation.updatedAt());
    }

    private static MapSqlParameterSource key(String idempotencyKey) {
        return new MapSqlParameterSource("idempotency_key", idempotencyKey);
    }

    private static MapSqlParameterSource claim(String idempotencyKey, long claimGeneration) {
        return key(idempotencyKey).addValue("claim_generation", claimGeneration);
    }

    private static String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 400));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
