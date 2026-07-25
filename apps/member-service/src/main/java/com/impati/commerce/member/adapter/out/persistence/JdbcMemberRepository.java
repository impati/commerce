package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.MemberRepository;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 */
@Repository
public class JdbcMemberRepository implements MemberRepository {
    private static final String MEMBER_COLUMNS = "id, email, name, status";

    private static final String INSERT_MEMBER = """
            insert into members (id, email, email_normalized, name, status)
            values (:id, :email, :email_normalized, :name, :status)
            """;

    private static final String UPDATE_MEMBER = """
            update members
               set email = :email,
                   email_normalized = :email_normalized,
                   name = :name,
                   status = :status
             where id = :id
            """;

    private static final String SELECT_BY_ID = "select " + MEMBER_COLUMNS + " from members where id = :id";
    private static final String SELECT_BY_EMAIL =
            "select " + MEMBER_COLUMNS + " from members where email_normalized = :email_normalized";
    private static final String SELECT_ALL = "select " + MEMBER_COLUMNS + " from members order by id";

    private static final String DELETE_ADDRESSES = "delete from member_addresses where member_id = :member_id";
    private static final String INSERT_ADDRESS = """
            insert into member_addresses (
                id, member_id, address_no, alias, recipient, phone, line1, city, postal_code, default_address
            ) values (
                :id, :member_id, :address_no, :alias, :recipient, :phone, :line1, :city, :postal_code,
                :default_address
            )
            """;
    private static final String SELECT_ADDRESSES = """
            select id, alias, recipient, phone, line1, city, postal_code, default_address
              from member_addresses
             where member_id = :member_id
             order by address_no
            """;

    private static final RowMapper<Address> ADDRESS_MAPPER = (rs, rowNum) -> Address.restore(
            rs.getString("id"),
            rs.getString("alias"),
            rs.getString("recipient"),
            rs.getString("phone"),
            rs.getString("line1"),
            rs.getString("city"),
            rs.getString("postal_code"),
            rs.getBoolean("default_address")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemberRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Member member) {
        var params = new MapSqlParameterSource()
                .addValue("id", member.id())
                .addValue("email", member.email())
                .addValue("email_normalized", normalize(member.email()))
                .addValue("name", member.name())
                .addValue("status", member.status());
        if (jdbc.update(UPDATE_MEMBER, params) == 0) {
            jdbc.update(INSERT_MEMBER, params);
        }

        jdbc.update(DELETE_ADDRESSES, new MapSqlParameterSource("member_id", member.id()));
        var addresses = member.addresses();
        for (var index = 0; index < addresses.size(); index++) {
            var address = addresses.get(index);
            jdbc.update(INSERT_ADDRESS, new MapSqlParameterSource()
                    .addValue("id", address.id())
                    .addValue("member_id", member.id())
                    .addValue("address_no", index)
                    .addValue("alias", address.alias())
                    .addValue("recipient", address.recipient())
                    .addValue("phone", address.phone())
                    .addValue("line1", address.line1())
                    .addValue("city", address.city())
                    .addValue("postal_code", address.postalCode())
                    .addValue("default_address", address.defaultAddress()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findById(String memberId) {
        return jdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", memberId), memberMapper())
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findByEmail(String email) {
        return jdbc.query(
                        SELECT_BY_EMAIL,
                        new MapSqlParameterSource("email_normalized", normalize(email)),
                        memberMapper()
                )
                .stream()
                .findFirst();
    }

    /** 로케일에 따라 결과가 달라지지 않게 ROOT로 고정한다. */
    private static String normalize(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Member> findAll() {
        return jdbc.query(SELECT_ALL, memberMapper());
    }

    private RowMapper<Member> memberMapper() {
        return (rs, rowNum) -> Member.restore(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("name"),
                findAddresses(rs.getString("id"))
        );
    }

    private List<Address> findAddresses(String memberId) {
        return jdbc.query(SELECT_ADDRESSES, new MapSqlParameterSource("member_id", memberId), ADDRESS_MAPPER);
    }
}
