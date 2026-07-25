package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.application.NotificationRepository;
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
 * <p>기록 순서는 identity 컬럼 {@code seq}로 유지한다. 알림은 수정되지 않으므로 저장은 insert뿐이다.
 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {
    private static final String INSERT = """
            insert into notifications (id, event_type, member_id, subject, body)
            values (:id, :event_type, :member_id, :subject, :body)
            """;

    private static final String SELECT_ALL = """
            select id, event_type, member_id, subject, body
              from notifications
             order by seq
            """;

    private static final RowMapper<Notification> ROW_MAPPER = (rs, rowNum) -> Notification.restore(
            rs.getString("id"),
            rs.getString("event_type"),
            rs.getString("member_id"),
            rs.getString("subject"),
            rs.getString("body")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Notification notification) {
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", notification.id())
                .addValue("event_type", notification.eventType())
                .addValue("member_id", notification.memberId())
                .addValue("subject", notification.subject())
                .addValue("body", notification.body()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findAll() {
        return jdbc.query(SELECT_ALL, ROW_MAPPER);
    }
}
