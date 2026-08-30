package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.domain.OrderModels.OrderEvent;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import com.impati.commerce.order.domain.OrderModels.PublishStatus;
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

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 컴파일러도
 * DB도 잡지 못하고, 쓰기와 읽기가 같은 방향으로 틀리면 왕복 테스트조차 통과한다.
 *
 * <p>{@code select *}도 쓰지 않는다. 컬럼이 추가되면 결과셋 모양이 말없이 바뀐다.
 */
@Repository
public class JdbcOrderEventRepository implements OrderEventRepository {
    private static final String COLUMNS = """
            id, type, order_id, member_id, payload, publish_status, attempts, last_error
            """;

    private static final String INSERT = """
            insert into order_events (
                id, type, order_id, member_id, payload, publish_status, attempts, last_error
            ) values (
                :id, :type, :order_id, :member_id, :payload, :publish_status, :attempts, :last_error
            )
            """;

    /**
     * 발행할 수 있는 사건을 <b>잠그며 고른다</b> (ADR-0011과 같은 방식).
     *
     * <p><b>{@code for update skip locked}가 배타성을 만든다.</b> 이 문장이 고른 행에는 커밋까지
     * 유지되는 배타 락이 걸리고, 다른 인스턴스는 그 행들을 건너뛴다. 그래서 진 쪽이 빈손이 되지
     * 않고 다른 사건을 받아 간다.
     *
     * <p>한 문장으로 합치지 않는 이유는 MySQL이 {@code update t ... where id in (select ... from t)}를
     * 거절하기 때문이다(ER 1093). 나누어도 <b>원자성은 그대로다</b> — 락이 커밋까지 유지되므로
     * 두 문장 사이에 다른 인스턴스가 끼어들 수 없다 (ADR-0013).
     */
    private static final String SELECT_CLAIMABLE = """
            select id
              from order_events
             where publish_status = 'PENDING'
               and (next_attempt_after is null or next_attempt_after <= :now)
             order by seq
             limit :limit
               for update skip locked
            """;

    /** 방금 잠근 사건들에 이 주기의 식별자와 다음 시도 시각을 새긴다. */
    private static final String CLAIM_FOR_PUBLISH = """
            update order_events
               set tx_id = :tx_id,
                   next_attempt_after = :retry_after
             where id in (:ids)
            """;

    /**
     * 방금 집은 묶음을 읽는다.
     *
     * <p>상태로도 거른다. 점유 식별자만으로 거르면 식별자가 재사용됐을 때 예전 주기의 종단된
     * 사건이 딸려 나온다 (BL-0010).
     */
    private static final String SELECT_CLAIMED = "select " + COLUMNS
            + " from order_events where tx_id = :tx_id and publish_status = 'PENDING' order by seq";

    private static final String UPDATE_PUBLISH_RESULT = """
            update order_events
               set publish_status = :publish_status,
                   attempts = :attempts,
                   last_error = :last_error
             where id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final OrderEventPayloadCodec payloadCodec;

    public JdbcOrderEventRepository(
            NamedParameterJdbcTemplate jdbc, Clock clock, OrderEventPayloadCodec payloadCodec) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.payloadCodec = payloadCodec;
    }

    /**
     * 트랜잭션을 열지 않는다. 이 쓰기는 주문 저장과 함께 성립해야 하는 구간의 일부이고,
     * 그 구간을 여는 것은 {@code OrderChanges}다. 여기서 따로 열면 단위가 둘로 쪼개진다.
     */
    @Override
    public void saveAll(List<OrderEvent> events) {
        for (var event : events) {
            jdbc.update(INSERT, params(event));
        }
    }

    /**
     * 격리 수준을 낮춘다.
     *
     * <p>MySQL 기본값(REPEATABLE READ)에서 {@code for update} 범위 읽기는 갭 락까지 잡는다. 이
     * 테이블은 큐라서 삽입이 계속 일어나므로 점유와 삽입이 교착한다. 낮출 이유가 이 경로에만
     * 있으므로 낮추는 범위도 여기까지다 (ADR-0013).
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<OrderEvent> claimForPublish(String publishId, int batchSize, Duration retryDelay) {
        var now = now();
        var ids = jdbc.queryForList(
                SELECT_CLAIMABLE,
                new MapSqlParameterSource().addValue("now", now).addValue("limit", batchSize),
                String.class);
        if (ids.isEmpty()) {
            return List.of();
        }
        jdbc.update(CLAIM_FOR_PUBLISH, new MapSqlParameterSource()
                .addValue("tx_id", publishId)
                .addValue("retry_after", now.plus(retryDelay))
                .addValue("ids", ids));
        return jdbc.query(SELECT_CLAIMED, new MapSqlParameterSource("tx_id", publishId), rowMapper());
    }

    @Override
    @Transactional
    public void savePublishResult(OrderEvent event) {
        jdbc.update(UPDATE_PUBLISH_RESULT, new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("publish_status", event.publishStatus().name())
                .addValue("attempts", event.attempts())
                .addValue("last_error", event.lastError()));
    }

    private RowMapper<OrderEvent> rowMapper() {
        return (rs, rowNum) -> OrderEvent.restore(
                rs.getString("id"),
                OrderEventType.valueOf(rs.getString("type")),
                rs.getString("order_id"),
                rs.getString("member_id"),
                payloadCodec.decode(rs.getString("payload")),
                PublishStatus.valueOf(rs.getString("publish_status")),
                rs.getInt("attempts"),
                rs.getString("last_error")
        );
    }

    private MapSqlParameterSource params(OrderEvent event) {
        return new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("type", event.type().name())
                .addValue("order_id", event.orderId())
                .addValue("member_id", event.memberId())
                .addValue("payload", payloadCodec.encode(event.payload()))
                .addValue("publish_status", event.publishStatus().name())
                .addValue("attempts", event.attempts())
                .addValue("last_error", event.lastError());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
