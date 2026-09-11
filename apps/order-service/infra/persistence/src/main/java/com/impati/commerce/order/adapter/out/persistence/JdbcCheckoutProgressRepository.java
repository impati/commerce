package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.domain.CheckoutProgress;
import org.springframework.dao.DuplicateKeyException;
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

@Repository
public class JdbcCheckoutProgressRepository implements CheckoutProgressRepository {
    private static final String COLUMNS = """
            order_id, member_id, idempotency_key, request_fingerprint, payment_token,
            expected_cart_version, stage, outcome, failure_code, payment_cleanup_status,
            last_error, reservation_id, payment_id, shipment_id, tracking_number,
            resume_stage, next_attempt_at, lease_until, lease_generation
            """;
    private static final String SELECT_BY_ORDER =
            "select " + COLUMNS + " from checkout_progress where order_id = :order_id";
    private static final String SELECT_BY_KEY =
            "select " + COLUMNS + " from checkout_progress where member_id = :member_id and idempotency_key = :idempotency_key";
    private static final String INSERT = """
            insert into checkout_progress (
                order_id, member_id, idempotency_key, request_fingerprint, payment_token,
                expected_cart_version, stage, outcome, failure_code, payment_cleanup_status,
                last_error, reservation_id, payment_id, shipment_id, tracking_number,
                resume_stage, next_attempt_at, lease_until, lease_generation
            ) values (
                :order_id, :member_id, :idempotency_key, :request_fingerprint, :payment_token,
                :expected_cart_version, :stage, :outcome, :failure_code, :payment_cleanup_status,
                :last_error, :reservation_id, :payment_id, :shipment_id, :tracking_number,
                :resume_stage, :next_attempt_at, :lease_until, :lease_generation
            )
            """;
    private static final String UPDATE = """
            update checkout_progress
               set stage = :stage,
                   outcome = :outcome,
                   failure_code = :failure_code,
                   payment_cleanup_status = :payment_cleanup_status,
                   last_error = :last_error,
                   reservation_id = :reservation_id,
                   payment_id = :payment_id,
                   shipment_id = :shipment_id,
                   tracking_number = :tracking_number,
                   resume_stage = :resume_stage,
                   next_attempt_at = :next_attempt_at,
                   lease_until = :lease_until
             where order_id = :order_id
               and lease_generation = :expected_generation
            """;
    private static final String CLAIM = """
            update checkout_progress
               set lease_generation = lease_generation + 1,
                   lease_until = :lease_until
             where order_id = :order_id
               and stage not in ('COMPLETED', 'FAILED', 'ATTENTION_REQUIRED')
               and (next_attempt_at is null or next_attempt_at <= :now)
               and (lease_until is null or lease_until <= :now)
            """;
    private static final String FIND_RECOVERABLE = """
            select order_id
              from checkout_progress
             where stage not in ('COMPLETED', 'FAILED', 'ATTENTION_REQUIRED')
               and (next_attempt_at is null or next_attempt_at <= :now)
               and (lease_until is null or lease_until <= :now)
             order by next_attempt_at, order_id
             limit :batch_size
            """;
    private static final String REQUEUE = """
            update checkout_progress
               set stage = case
                       when outcome = 'FAILED' then 'COMPENSATING'
                       else coalesce(resume_stage, 'ACCEPTED')
                   end,
                   resume_stage = null,
                   next_attempt_at = :now,
                   lease_until = null,
                   last_error = null
             where order_id = :order_id
               and stage in ('ATTENTION_REQUIRED', 'COMPENSATING')
            """;

    private static final RowMapper<CheckoutProgress> ROW_MAPPER = (rs, rowNum) -> CheckoutProgress.restore(
            rs.getString("order_id"), rs.getString("member_id"), rs.getString("idempotency_key"),
            rs.getString("request_fingerprint"), rs.getString("payment_token"),
            rs.getLong("expected_cart_version"), rs.getString("stage"), rs.getString("outcome"),
            rs.getString("failure_code"), rs.getString("payment_cleanup_status"), rs.getString("last_error"),
            rs.getString("reservation_id"), rs.getString("payment_id"), rs.getString("shipment_id"),
            rs.getString("tracking_number"), rs.getString("resume_stage"),
            rs.getObject("next_attempt_at", OffsetDateTime.class),
            rs.getObject("lease_until", OffsetDateTime.class), rs.getLong("lease_generation"));

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcCheckoutProgressRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CheckoutProgress> findByOrderId(String orderId) {
        return jdbc.query(SELECT_BY_ORDER, new MapSqlParameterSource("order_id", orderId), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CheckoutProgress> findByMemberAndKey(String memberId, String idempotencyKey) {
        return jdbc.query(SELECT_BY_KEY, new MapSqlParameterSource()
                        .addValue("member_id", memberId)
                        .addValue("idempotency_key", idempotencyKey), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(CheckoutProgress progress) {
        try {
            jdbc.update(INSERT, params(progress).addValue("expected_generation", progress.leaseGeneration()));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    @Transactional
    public boolean save(CheckoutProgress progress, long leaseGeneration) {
        return jdbc.update(UPDATE, params(progress).addValue("expected_generation", leaseGeneration)) == 1;
    }

    @Override
    @Transactional
    public Optional<CheckoutProgress> claim(String orderId, Duration leaseDuration) {
        var now = now();
        if (jdbc.update(CLAIM, new MapSqlParameterSource()
                .addValue("order_id", orderId)
                .addValue("now", now)
                .addValue("lease_until", now.plus(leaseDuration))) == 0) {
            return Optional.empty();
        }
        return findByOrderId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findRecoverable(int batchSize) {
        return jdbc.queryForList(FIND_RECOVERABLE, new MapSqlParameterSource()
                .addValue("now", now())
                .addValue("batch_size", batchSize), String.class);
    }

    @Override
    @Transactional
    public boolean requeue(String orderId, OffsetDateTime now) {
        return jdbc.update(REQUEUE, new MapSqlParameterSource()
                .addValue("order_id", orderId)
                .addValue("now", now)) == 1;
    }

    private MapSqlParameterSource params(CheckoutProgress progress) {
        return new MapSqlParameterSource()
                .addValue("order_id", progress.orderId())
                .addValue("member_id", progress.memberId())
                .addValue("idempotency_key", progress.idempotencyKey())
                .addValue("request_fingerprint", progress.requestFingerprint())
                .addValue("payment_token", progress.paymentToken())
                .addValue("expected_cart_version", progress.expectedCartVersion())
                .addValue("stage", progress.stage().name())
                .addValue("outcome", progress.outcome().name())
                .addValue("failure_code", progress.failureCode())
                .addValue("payment_cleanup_status", progress.paymentCleanupStatus())
                .addValue("last_error", progress.lastError())
                .addValue("reservation_id", progress.reservationId())
                .addValue("payment_id", progress.paymentId())
                .addValue("shipment_id", progress.shipmentId())
                .addValue("tracking_number", progress.trackingNumber())
                .addValue("resume_stage", progress.resumeStage() == null ? null : progress.resumeStage().name())
                .addValue("next_attempt_at", progress.nextAttemptAt())
                .addValue("lease_until", progress.leaseUntil())
                .addValue("lease_generation", progress.leaseGeneration());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
