package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import com.impati.commerce.member.domain.MemberModels.PasswordHash;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 */
@Repository
public class JdbcMemberRepository implements MemberRepository {
    private static final String UX_MEMBERS_EMAIL = "ux_members_email";

    private static final String MEMBER_COLUMNS = "id, email, name, status, password_hash, address_book_version";

    private static final String INSERT_MEMBER = """
            insert into members (id, email, name, status, password_hash, address_book_version)
            values (:id, :email, :name, :status, :password_hash, :version)
            """;

    private static final String UPDATE_MEMBER = """
            update members
               set email = :email,
                   name = :name,
                   status = :status,
                   password_hash = :password_hash
             where id = :id
            """;

    private static final String UPDATE_ADDRESS_VERSION = """
            update members set address_book_version = :version
             where id = :id and address_book_version = :expected_version
            """;

    private static final String SELECT_BY_ID = "select " + MEMBER_COLUMNS + " from members where id = :id";
    private static final String SELECT_BY_EMAIL =
            "select " + MEMBER_COLUMNS + " from members where email = :email";

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
                .addValue("name", member.name())
                .addValue("status", member.status())
                .addValue("password_hash", member.passwordHash().value())
                .addValue("version", member.addressBookVersion())
                .addValue("expected_version", member.savedAddressBookVersion());
        if (!member.persisted()) {
            try {
                jdbc.update(INSERT_MEMBER, params);
            } catch (DuplicateKeyException e) {
                if (isDuplicateKeyFor(e, UX_MEMBERS_EMAIL)) {
                    throw DomainException.conflict("member email already exists");
                }
                throw e;
            }
        } else if (member.addressesChanged()) {
            if (jdbc.update(UPDATE_ADDRESS_VERSION, params) != 1) {
                throw DomainException.addressBookChanged("주소록이 변경됐습니다. 최신 목록을 확인하고 다시 조작해주세요.");
            }
        } else {
            // 인증 상태 저장은 주소록의 조회 사본을 다시 쓰지 않는다.
            jdbc.update(UPDATE_MEMBER, params);
            return;
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
        member.markSaved();
    }

    private boolean isDuplicateKeyFor(final DuplicateKeyException e, final String keyName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(keyName);
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
                        new MapSqlParameterSource("email", email),
                        memberMapper()
                )
                .stream()
                .findFirst();
    }

    private RowMapper<Member> memberMapper() {
        return (rs, rowNum) -> Member.restore(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("name"),
                new PasswordHash(rs.getString("password_hash")),
                rs.getString("status"),
                findAddresses(rs.getString("id")),
                rs.getLong("address_book_version")
        );
    }

    private List<Address> findAddresses(String memberId) {
        return jdbc.query(SELECT_ADDRESSES, new MapSqlParameterSource("member_id", memberId), ADDRESS_MAPPER);
    }
}
