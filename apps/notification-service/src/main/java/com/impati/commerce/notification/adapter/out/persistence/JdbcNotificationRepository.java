package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Channel;
import com.impati.commerce.notification.domain.NotificationModels.DeliveryStatus;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 *
 * <p>기록 순서는 identity 컬럼 {@code seq}로 유지한다.
 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {
    private static final String COLUMNS = """
            id, event_type, member_id, subject, body,
            channel, recipient, delivery_status, attempts, last_error
            """;

    private static final String INSERT = """
            insert into notifications (
                id, event_type, member_id, subject, body,
                channel, recipient, delivery_status, attempts, last_error
            ) values (
                :id, :event_type, :member_id, :subject, :body,
                :channel, :recipient, :delivery_status, :attempts, :last_error
            )
            """;

    private static final String UPDATE = """
            update notifications
               set delivery_status = :delivery_status,
                   attempts = :attempts,
                   last_error = :last_error
             where id = :id
            """;

    private static final String SELECT_ALL = "select " + COLUMNS + " from notifications order by seq";

    private static final String SELECT_BY_MEMBER = "select " + COLUMNS
            + " from notifications where member_id = :member_id order by seq";

    private static final String SELECT_PENDING_MAIL = """
            select
            """ + COLUMNS + """
              from notifications
             where channel = 'MAIL' and delivery_status = 'PENDING'
             order by seq
             limit :limit
            """;

    private static final RowMapper<Notification> ROW_MAPPER = (rs, rowNum) -> Notification.restore(
            rs.getString("id"),
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

    public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Notification notification) {
        var params = new MapSqlParameterSource()
                .addValue("id", notification.id())
                .addValue("event_type", notification.eventType())
                .addValue("member_id", notification.memberId())
                .addValue("subject", notification.subject())
                .addValue("body", notification.body())
                .addValue("channel", notification.channel().name())
                .addValue("recipient", notification.recipient())
                .addValue("delivery_status", notification.deliveryStatus().name())
                .addValue("attempts", notification.attempts())
                .addValue("last_error", notification.lastError());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
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
    public List<Notification> findPendingMail(int limit) {
        return jdbc.query(SELECT_PENDING_MAIL, new MapSqlParameterSource("limit", limit), ROW_MAPPER);
    }
}
