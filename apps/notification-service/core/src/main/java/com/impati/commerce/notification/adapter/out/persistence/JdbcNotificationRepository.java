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
import org.springframework.transaction.annotation.Isolation;
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
     * 멱등 키와 점유 컬럼은 갱신하지 않는다.
     *
     * <p>키는 기록 시점에 정해지고 바뀌지 않는다. 점유는 작업 큐의 사정이라 애그리거트가
     * 모르며 {@link #claimForDispatch}만 정한다 — 여기서 함께 덮으면 저장 한 번에 점유가
     * 지워져 다른 인스턴스가 곧바로 같은 건을 집는다.
     */
    private static final String UPDATE = """
            update notifications
               set delivery_status = :delivery_status,
                   attempts = :attempts,
                   last_error = :last_error
             where id = :id
            """;

    private static final String SELECT_ALL = "select " + COLUMNS + " from notifications order by seq";

    private static final String SELECT_BY_IDEMPOTENCY_KEY = "select " + COLUMNS
            + " from notifications where idempotency_key = :idempotency_key";

    private static final String SELECT_BY_MEMBER = "select " + COLUMNS
            + " from notifications where member_id = :member_id order by seq";

    /**
     * 보낼 수 있는 것들을 <b>잠그며 고른다</b>.
     *
     * <p><b>{@code for update skip locked}가 배타성을 만든다.</b> 이 문장이 고른 행에는 커밋까지
     * 유지되는 배타 락이 걸리고, 다른 인스턴스가 같은 문장을 돌리면 그 행들을 건너뛴다. 그래서
     * 두 인스턴스가 같은 묶음을 손에 들고 나란히 발송하는 일이 생기지 않는다.
     *
     * <p>한 문장으로 합치지 않는 이유는 MySQL이 {@code update t ... where id in (select ... from t)}를
     * 거절하기 때문이다(ER 1093). 파생 테이블로 감싸면 통과하지만 materialize되어 잠금이 의미를
     * 잃는다. 나누어도 <b>원자성은 그대로다</b> — 락이 커밋까지 유지되므로 두 문장 사이에 다른
     * 인스턴스가 끼어들 수 없다.
     */
    private static final String SELECT_CLAIMABLE = """
            select id
              from notifications
             where channel = 'MAIL'
               and delivery_status = 'PENDING'
               and (next_attempt_after is null or next_attempt_after <= :now)
             order by seq
             limit :limit
               for update skip locked
            """;

    /** 방금 잠근 것들에 이 주기의 식별자와 다음 시도 시각을 새긴다. */
    private static final String CLAIM_FOR_DISPATCH = """
            update notifications
               set tx_id = :tx_id,
                   next_attempt_after = :retry_after
             where id in (:ids)
            """;

    /**
     * 방금 집은 묶음을 읽는다.
     *
     * <p>상태로도 거른다. 점유 식별자만으로 거르면 식별자가 재사용됐을 때 예전 주기의 종단된
     * 행이 딸려 나온다 (BL-0010).
     */
    private static final String SELECT_CLAIMED = "select " + COLUMNS
            + " from notifications where tx_id = :tx_id and delivery_status = 'PENDING' order by seq";

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

    /**
     * 격리 수준을 낮춘다.
     *
     * <p>MySQL 기본값(REPEATABLE READ)에서 {@code for update} 범위 읽기는 갭 락까지 잡는다. 이
     * 테이블은 큐라서 삽입이 계속 일어나므로 점유와 삽입이 교착한다. READ COMMITTED에는 갭 락이
     * 없다.
     *
     * <p>전역이 아니라 여기만 낮추는 이유는, 낮출 이유가 이 경로에만 있기 때문이다. 애그리거트를
     * 여러 테이블에서 읽는 조회들은 한 스냅샷을 봐야 하고, 그 성질까지 함께 버릴 이유가 없다
     * (ADR-0013).
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Notification> claimForDispatch(String dispatchId, int batchSize, Duration retryDelay) {
        var now = now();
        var ids = jdbc.queryForList(
                SELECT_CLAIMABLE,
                new MapSqlParameterSource().addValue("now", now).addValue("limit", batchSize),
                String.class);
        if (ids.isEmpty()) {
            return List.of();
        }
        jdbc.update(CLAIM_FOR_DISPATCH, new MapSqlParameterSource()
                .addValue("tx_id", dispatchId)
                .addValue("retry_after", now.plus(retryDelay))
                .addValue("ids", ids));
        return jdbc.query(SELECT_CLAIMED, new MapSqlParameterSource("tx_id", dispatchId), ROW_MAPPER);
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
