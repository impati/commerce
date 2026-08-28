package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.port.out.VerificationMailRepository;
import com.impati.commerce.member.domain.MemberModels.VerificationMail;
import com.impati.commerce.member.domain.MemberModels.VerificationMailStatus;
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
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 *
 * <p>기록 순서는 identity 컬럼 {@code seq}로 유지한다.
 */
@Repository
public class JdbcVerificationMailRepository implements VerificationMailRepository {
    private static final String COLUMNS = "id, member_id, email, token, status, attempts, last_error";

    private static final String INSERT = """
            insert into verification_mails (id, member_id, email, token, status, attempts, last_error)
            values (:id, :member_id, :email, :token, :status, :attempts, :last_error)
            """;

    /**
     * 점유 컬럼을 건드리지 않는다.
     *
     * <p>{@code next_attempt_after}를 여기서 함께 쓰면 처리 중 저장이 자기 점유를 지운다.
     * 그 컬럼은 저장소가 {@link #claimForDispatch}에서만 정한다.
     */
    private static final String UPDATE = """
            update verification_mails
               set token = :token,
                   status = :status,
                   attempts = :attempts,
                   last_error = :last_error
             where id = :id
            """;

    private static final String SELECT = "select " + COLUMNS + " from verification_mails where id = :id";

    private static final String SELECT_CANDIDATES = """
            select id
              from verification_mails
             where status = 'PENDING'
               and (next_attempt_after is null or next_attempt_after <= :now)
             order by seq
             limit :limit
            """;

    private static final String CLAIM_FOR_DISPATCH = """
            update verification_mails
               set next_attempt_after = :retry_after
             where id = :id
               and status = 'PENDING'
               and (next_attempt_after is null or next_attempt_after <= :now)
            """;

    private static final RowMapper<VerificationMail> ROW_MAPPER = (rs, rowNum) -> VerificationMail.restore(
            rs.getString("id"),
            rs.getString("member_id"),
            rs.getString("email"),
            rs.getString("token"),
            VerificationMailStatus.valueOf(rs.getString("status")),
            rs.getInt("attempts"),
            rs.getString("last_error")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcVerificationMailRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(VerificationMail mail) {
        var params = new MapSqlParameterSource()
                .addValue("id", mail.id())
                .addValue("member_id", mail.memberId())
                .addValue("email", mail.email())
                .addValue("token", mail.token())
                .addValue("status", mail.status().name())
                .addValue("attempts", mail.attempts())
                .addValue("last_error", mail.lastError());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findDispatchCandidates(int batchSize) {
        return jdbc.queryForList(SELECT_CANDIDATES, new MapSqlParameterSource()
                .addValue("now", now())
                .addValue("limit", batchSize), String.class);
    }

    @Override
    @Transactional
    public Optional<VerificationMail> claimForDispatch(String mailId, Duration retryDelay) {
        var now = now();
        var claimed = jdbc.update(CLAIM_FOR_DISPATCH, new MapSqlParameterSource()
                .addValue("id", mailId)
                .addValue("now", now)
                .addValue("retry_after", now.plus(retryDelay)));
        if (claimed == 0) {
            return Optional.empty();
        }
        return jdbc.query(SELECT, new MapSqlParameterSource("id", mailId), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
