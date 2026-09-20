package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.domain.CancellationProgress;
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
public class JdbcCancellationProgressRepository implements CancellationProgressRepository {
    private static final String COLUMNS = """
            order_id, member_id, stage, failure_code, last_error, resume_stage,
            next_attempt_at, lease_until, lease_generation
            """;
    private static final String INSERT = """
            insert into cancellation_progress (
                order_id, member_id, stage, failure_code, last_error, resume_stage,
                next_attempt_at, lease_until, lease_generation
            ) values (
                :order_id, :member_id, :stage, :failure_code, :last_error, :resume_stage,
                :next_attempt_at, :lease_until, :lease_generation
            )
            """;
    private static final String UPDATE = """
            update cancellation_progress
               set stage = :stage,
                   failure_code = :failure_code,
                   last_error = :last_error,
                   resume_stage = :resume_stage,
                   next_attempt_at = :next_attempt_at,
                   lease_until = :lease_until
             where order_id = :order_id
               and lease_generation = :expected_generation
            """;
    private static final String CLAIM = """
            update cancellation_progress
               set lease_generation = lease_generation + 1,
                   lease_until = :lease_until
             where order_id = :order_id
               and stage not in ('COMPLETED', 'REJECTED', 'ATTENTION_REQUIRED')
               and (next_attempt_at is null or next_attempt_at <= :now)
               and (lease_until is null or lease_until <= :now)
            """;
    private static final String FIND_RECOVERABLE = """
            select order_id
              from cancellation_progress
             where stage not in ('COMPLETED', 'REJECTED', 'ATTENTION_REQUIRED')
               and (next_attempt_at is null or next_attempt_at <= :now)
               and (lease_until is null or lease_until <= :now)
             order by next_attempt_at, order_id
             limit :batch_size
            """;
    private static final String REQUEUE = """
            update cancellation_progress
               set stage = coalesce(resume_stage, 'REQUESTED'),
                   resume_stage = null,
                   next_attempt_at = :now,
                   lease_until = null,
                   last_error = null
             where order_id = :order_id
               and stage = 'ATTENTION_REQUIRED'
            """;
    private static final RowMapper<CancellationProgress> ROW_MAPPER = (rs, rowNum) -> CancellationProgress.restore(
            rs.getString("order_id"), rs.getString("member_id"), rs.getString("stage"),
            rs.getString("failure_code"), rs.getString("last_error"), rs.getString("resume_stage"),
            rs.getObject("next_attempt_at", OffsetDateTime.class),
            rs.getObject("lease_until", OffsetDateTime.class), rs.getLong("lease_generation"));

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcCancellationProgressRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CancellationProgress> findByOrderId(String orderId) {
        return jdbc.query("select " + COLUMNS + " from cancellation_progress where order_id = :order_id",
                new MapSqlParameterSource("order_id", orderId), ROW_MAPPER).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CancellationProgress> findByOrderIds(String memberId, List<String> orderIds) {
        if (orderIds.isEmpty()) return List.of();
        return jdbc.query("select " + COLUMNS
                        + " from cancellation_progress where member_id = :member_id and order_id in (:order_ids)",
                new MapSqlParameterSource("member_id", memberId).addValue("order_ids", orderIds), ROW_MAPPER);
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(CancellationProgress progress) {
        try {
            jdbc.update(INSERT, params(progress));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    @Transactional
    public boolean save(CancellationProgress progress, long leaseGeneration) {
        return jdbc.update(UPDATE, params(progress).addValue("expected_generation", leaseGeneration)) == 1;
    }

    @Override
    @Transactional
    public Optional<CancellationProgress> claim(String orderId, Duration leaseDuration) {
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
                .addValue("now", now()).addValue("batch_size", batchSize), String.class);
    }

    @Override
    @Transactional
    public boolean requeue(String orderId, OffsetDateTime now) {
        return jdbc.update(REQUEUE, new MapSqlParameterSource()
                .addValue("order_id", orderId).addValue("now", now)) == 1;
    }

    private MapSqlParameterSource params(CancellationProgress progress) {
        return new MapSqlParameterSource()
                .addValue("order_id", progress.orderId())
                .addValue("member_id", progress.memberId())
                .addValue("stage", progress.stage().name())
                .addValue("failure_code", progress.failureCode())
                .addValue("last_error", progress.lastError())
                .addValue("resume_stage", progress.resumeStage() == null ? null : progress.resumeStage().name())
                .addValue("next_attempt_at", progress.nextAttemptAt())
                .addValue("lease_until", progress.leaseUntil())
                .addValue("lease_generation", progress.leaseGeneration());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
