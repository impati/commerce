package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Channel;
import com.impati.commerce.notification.domain.NotificationModels.DeliveryStatus;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
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

/**
 * 알림 저장소의 JDBC 구현.
 *
 * <p>이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 *
 * <p>기록 순서는 identity 컬럼 {@code seq}로 유지한다.
 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {
    private static final String COLUMNS = """
            id, idempotency_key, event_type, member_id, subject, body,
            channel, recipient, delivery_status, attempts, last_error
            """;

    private static final String INSERT = """
            insert into notifications (
                id, idempotency_key, event_type, member_id, subject, body,
                channel, recipient, delivery_status, attempts, last_error
            ) values (
                :id, :idempotency_key, :event_type, :member_id, :subject, :body,
                :channel, :recipient, :delivery_status, :attempts, :last_error
            )
            """;

    /**
     * 멱등 키와 다음 시도 시각은 갱신하지 않는다.
     *
     * <p>키는 기록 시점에 정해지고 바뀌지 않는다. 다음 시도 시각은 작업 큐의 사정이라
     * 애그리거트가 모르며, {@link #claimForDispatch}만 정한다 — 여기서 함께 덮으면 저장 한 번에
     * 점유가 지워져 다른 인스턴스가 곧바로 같은 건을 집는다.
     */
    private static final String UPDATE = """
            update notifications
               set delivery_status = :delivery_status,
                   attempts = :attempts,
                   last_error = :last_error
             where id = :id
            """;

    private static final String SELECT_ALL = "select " + COLUMNS + " from notifications order by seq";

    private static final String SELECT_BY_ID = "select " + COLUMNS + " from notifications where id = :id";

    private static final String SELECT_BY_IDEMPOTENCY_KEY = "select " + COLUMNS
            + " from notifications where idempotency_key = :idempotency_key";

    private static final String SELECT_BY_MEMBER = "select " + COLUMNS
            + " from notifications where member_id = :member_id order by seq";

    private static final String SELECT_DISPATCH_CANDIDATES = """
            select id
              from notifications
             where channel = 'MAIL'
               and delivery_status = 'PENDING'
               and (next_attempt_after is null or next_attempt_after <= :now)
             order by seq
             limit :limit
            """;

    private static final String CLAIM_FOR_DISPATCH = """
            update notifications
               set next_attempt_after = :retry_after
             where id = :id
               and channel = 'MAIL'
               and delivery_status = 'PENDING'
               and (next_attempt_after is null or next_attempt_after <= :now)
            """;

    private static final RowMapper<Notification> ROW_MAPPER = (rs, rowNum) -> Notification.restore(
            rs.getString("id"),
            rs.getString("idempotency_key"),
            rs.getString("event_type"),
            rs.getString("member_id"),
            rs.getString("subject"),
            rs.getString("body"),
            Channel.valueOf(rs.getString("channel")),
            rs.getString("recipient"),
            DeliveryStatus.valueOf(rs.getString("delivery_status")),
            rs.getInt("attempts"),
            rs.getString("last_error")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(Notification notification) {
        var params = params(notification);
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
    }

    /**
     * 트랜잭션으로 묶지 않는다.
     *
     * <p>제약 위반 뒤에 이어서 읽어야 하는데, 실패한 문장이 트랜잭션을 abort 상태로 만드는
     * DB에서는 그 읽기가 함께 실패한다. 중복을 처리하려고 만든 경로가 정확히 중복일 때 깨진다.
     * 문장이 각각 커밋되면 INSERT 실패가 아무것도 오염시키지 않는다.
     */
    @Override
    public Notification saveIfAbsent(Notification notification) {
        try {
            jdbc.update(INSERT, params(notification));
            return notification;
        } catch (DuplicateKeyException alreadyRecorded) {
            return findByIdempotencyKey(notification.idempotencyKey())
                    .orElseThrow(() -> alreadyRecorded);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findAll() {
        return jdbc.query(SELECT_ALL, ROW_MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findByMemberId(String memberId) {
        return jdbc.query(SELECT_BY_MEMBER, new MapSqlParameterSource("member_id", memberId), ROW_MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findDispatchCandidates(int batchSize) {
        return jdbc.queryForList(SELECT_DISPATCH_CANDIDATES, new MapSqlParameterSource()
                .addValue("now", now())
                .addValue("limit", batchSize), String.class);
    }

    @Override
    @Transactional
    public Optional<Notification> claimForDispatch(String notificationId, Duration retryDelay) {
        var now = now();
        var claimed = jdbc.update(CLAIM_FOR_DISPATCH, new MapSqlParameterSource()
                .addValue("id", notificationId)
                .addValue("now", now)
                .addValue("retry_after", now.plus(retryDelay)));
        if (claimed == 0) {
            return Optional.empty();
        }
        return jdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", notificationId), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private Optional<Notification> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query(
                        SELECT_BY_IDEMPOTENCY_KEY,
                        new MapSqlParameterSource("idempotency_key", idempotencyKey),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private MapSqlParameterSource params(Notification notification) {
        return new MapSqlParameterSource()
                .addValue("id", notification.id())
                .addValue("idempotency_key", notification.idempotencyKey())
                .addValue("event_type", notification.eventType())
                .addValue("member_id", notification.memberId())
                .addValue("subject", notification.subject())
                .addValue("body", notification.body())
                .addValue("channel", notification.channel().name())
                .addValue("recipient", notification.recipient())
                .addValue("delivery_status", notification.deliveryStatus().name())
                .addValue("attempts", notification.attempts())
                .addValue("last_error", notification.lastError());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
