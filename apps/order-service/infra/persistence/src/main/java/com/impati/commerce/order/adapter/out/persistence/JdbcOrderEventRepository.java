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
import java.util.Set;

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
     * 점유할 <b>주문</b>을 고른다 (ADR-0016).
     *
     * <p>점유 단위가 사건이 아니라 주문인 것이 순서 보장의 근거다. 사건 단위로 집으면 같은
     * 주문의 두 사건을 다른 인스턴스가 나눠 가질 수 있고, 그러면 <b>둘 다 성공해도</b> 끝나는
     * 순서가 발행 순서가 된다. 실패가 없어도 깨지므로 인스턴스 하나로 도는 동안에는 드러나지
     * 않는다.
     *
     * <p><b>{@code max}로 거르는 이유.</b> 그 주문의 사건이 <b>전부</b> 시도할 때가 됐을 때만
     * 집는다. 하나라도 백오프 중이면 그 뒤 사건도 기다려야 한다 — 먼저 보낼 수 없는 것을 두고
     * 뒤를 보내면 순서가 깨진다. {@code next_attempt_after}가 null인 것은 아직 시도한 적이
     * 없다는 뜻이라 지금이 시도할 때이므로 {@code :now}로 채운다.
     *
     * <p>여기서는 잠그지 않는다. {@code group by}가 있는 잠금 읽기를 피하는 것이기도 하고,
     * 배타성은 다음 문장의 조건부 UPDATE가 만들기 때문이다.
     */
    private static final String SELECT_CLAIMABLE_ORDERS = """
            select order_id
              from order_events
             where publish_status = 'PENDING'
             group by order_id
            having max(coalesce(next_attempt_after, :now)) <= :now
             order by min(seq)
             limit :limit
            """;

    /**
     * 고른 주문들의 미발행 사건에 이 주기의 식별자와 다음 시도 시각을 새긴다.
     *
     * <p><b>이 UPDATE가 배타성을 만든다.</b> {@code next_attempt_after} 조건이 걸려 있으므로,
     * 다른 인스턴스가 먼저 집어 시각을 미래로 밀어두었으면 한 행도 맞지 않는다. 진 쪽은 그
     * 주문을 통째로 놓치고 다른 주문을 받아 간다.
     *
     * <p>{@code skip locked}를 쓰지 않는다. 그것은 잠긴 행을 건너뛰므로 <b>한 주문의 사건 일부만</b>
     * 집힐 수 있고, 그러면 이 결정이 막으려는 상황이 그대로 생긴다. UPDATE는 건너뛰지 않고
     * 기다렸다가 갱신된 값으로 다시 판정한다.
     */
    private static final String CLAIM_ORDERS_FOR_PUBLISH = """
            update order_events
               set tx_id = :tx_id,
                   next_attempt_after = :retry_after
             where order_id in (:order_ids)
               and publish_status = 'PENDING'
               and (next_attempt_after is null or next_attempt_after <= :now)
            """;

    /**
     * 미발행 사건이 남았는데 우리 것이 아닌 주문을 찾는다.
     *
     * <p>고르는 것과 집는 것 사이에 다른 인스턴스가 끼어들면 <b>일부만</b> 집힐 수 있다. 그
     * 주문의 사건을 보내면 앞 건을 건너뛴 채 뒤 건을 보내는 것이 되므로 이번 주기에서 통째로
     * 뺀다. 남겨둔 것은 다음 주기가 온전히 집는다.
     */
    private static final String SELECT_INCOMPLETE_ORDERS = """
            select order_id
              from order_events
             where order_id in (:order_ids)
               and publish_status = 'PENDING'
             group by order_id
            having sum(case when tx_id = :tx_id then 0 else 1 end) > 0
            """;

    /**
     * 방금 집은 묶음을 읽는다.
     *
     * <p>상태로도 거른다. 점유 식별자만으로 거르면 식별자가 재사용됐을 때 예전 주기의 종단된
     * 사건이 딸려 나온다 (BL-0010).
     */
    private static final String SELECT_CLAIMED = "select " + COLUMNS
            + " from order_events where tx_id = :tx_id and publish_status = 'PENDING'"
            + " order by order_id, seq";

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
     * <p>MySQL 기본값(REPEATABLE READ)에서 범위 읽기와 갱신은 갭 락까지 잡는다. 이 테이블은
     * 큐라서 삽입이 계속 일어나므로 점유와 삽입이 교착한다. 낮출 이유가 이 경로에만 있으므로
     * 낮추는 범위도 여기까지다 (ADR-0013).
     *
     * <p>세 문장이 한 트랜잭션에 있다. 갱신이 잡은 배타 락이 커밋까지 유지되므로 문장 사이에
     * 다른 인스턴스가 같은 행을 고쳐 넣을 수 없다.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<OrderEvent> claimForPublish(String publishId, int orderBatchSize, Duration retryDelay) {
        var now = now();
        var orderIds = jdbc.queryForList(
                SELECT_CLAIMABLE_ORDERS,
                new MapSqlParameterSource().addValue("now", now).addValue("limit", orderBatchSize),
                String.class);
        if (orderIds.isEmpty()) {
            return List.of();
        }
        jdbc.update(CLAIM_ORDERS_FOR_PUBLISH, new MapSqlParameterSource()
                .addValue("tx_id", publishId)
                .addValue("retry_after", now.plus(retryDelay))
                .addValue("order_ids", orderIds)
                .addValue("now", now));

        var incomplete = Set.copyOf(jdbc.queryForList(
                SELECT_INCOMPLETE_ORDERS,
                new MapSqlParameterSource().addValue("order_ids", orderIds).addValue("tx_id", publishId),
                String.class));

        return jdbc.query(SELECT_CLAIMED, new MapSqlParameterSource("tx_id", publishId), rowMapper())
                .stream()
                .filter(event -> !incomplete.contains(event.orderId()))
                .toList();
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
