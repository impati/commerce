package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.port.out.SessionRepository;
import com.impati.commerce.member.domain.MemberModels.Session;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;

/** 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다. */
@Repository
public class JdbcSessionRepository implements SessionRepository {
    private static final String INSERT = """
            insert into member_sessions (token_hash, member_id, expires_at, revoked_at)
            values (:token_hash, :member_id, :expires_at, :revoked_at)
            """;
    private static final String UPDATE = """
            update member_sessions set revoked_at = :revoked_at where token_hash = :token_hash
            """;
    private static final String SELECT = """
            select token_hash, member_id, expires_at, revoked_at
              from member_sessions
             where token_hash = :token_hash
            """;

    private static final RowMapper<Session> ROW_MAPPER = (rs, rowNum) -> Session.restore(
            rs.getString("token_hash"),
            rs.getString("member_id"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSessionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Session session) {
        var params = new MapSqlParameterSource()
                .addValue("token_hash", session.tokenHash())
                .addValue("member_id", session.memberId())
                .addValue("expires_at", Timestamp.from(session.expiresAt()))
                .addValue("revoked_at", session.revokedAt() == null
                        ? null : Timestamp.from(session.revokedAt()));
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findByTokenHash(String tokenHash) {
        return jdbc.query(SELECT, new MapSqlParameterSource("token_hash", tokenHash), ROW_MAPPER)
                .stream()
                .findFirst();
    }
}
