package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.port.out.EmailVerificationRepository;
import com.impati.commerce.member.domain.MemberModels.EmailVerification;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;

/** 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다. */
@Repository
public class JdbcEmailVerificationRepository implements EmailVerificationRepository {
    private static final String INSERT = """
            insert into email_verifications (token_hash, member_id, expires_at, used_at)
            values (:token_hash, :member_id, :expires_at, :used_at)
            """;
    private static final String UPDATE = """
            update email_verifications set used_at = :used_at where token_hash = :token_hash
            """;
    private static final String SELECT = """
            select token_hash, member_id, expires_at, used_at
              from email_verifications
             where token_hash = :token_hash
            """;

    private static final RowMapper<EmailVerification> ROW_MAPPER = (rs, rowNum) -> EmailVerification.restore(
            rs.getString("token_hash"),
            rs.getString("member_id"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEmailVerificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(EmailVerification verification) {
        var params = new MapSqlParameterSource()
                .addValue("token_hash", verification.tokenHash())
                .addValue("member_id", verification.memberId())
                .addValue("expires_at", Timestamp.from(verification.expiresAt()))
                .addValue("used_at", verification.usedAt() == null
                        ? null : Timestamp.from(verification.usedAt()));
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailVerification> findByTokenHash(String tokenHash) {
        return jdbc.query(SELECT, new MapSqlParameterSource("token_hash", tokenHash), ROW_MAPPER)
                .stream()
                .findFirst();
    }
}
