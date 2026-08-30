package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import com.impati.commerce.member.domain.MemberModels.PasswordHash;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.TestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresDatabase
class JdbcMemberRepositoryTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry, "member-repo");
    }
    private static final PasswordHash HASH = new PasswordHash("$2a$10$fakehashforpersistencetest");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsMemberWithAddresses() {
        var member = new Member("mem_round", "round@impati.dev", "Round Tester", HASH);
        member.addAddress(address("home", "Seoul", false));
        member.addAddress(address("office", "Busan", true));

        memberRepository.save(member);
        var loaded = memberRepository.findById("mem_round").orElseThrow();

        assertThat(loaded.email()).isEqualTo("round@impati.dev");
        assertThat(loaded.name()).isEqualTo("Round Tester");
        assertThat(loaded.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(loaded.addresses()).hasSize(2);
        assertThat(loaded.addresses().stream().map(Address::alias)).containsExactly("home", "office");
    }

    /**
     * 기본 배송지가 복원 후에도 유지돼야 한다. addAddress를 거쳐 복원하면 첫 주소가 강제로
     * 기본이 되므로 저장된 값과 달라진다. Member.restore가 그 경로를 우회하는 이유다.
     */
    @Test
    void keepsWhichAddressIsDefault() {
        var member = new Member("mem_default", "default@impati.dev", "Default Tester", HASH);
        member.addAddress(address("home", "Seoul", false));
        member.addAddress(address("office", "Busan", true));
        memberRepository.save(member);

        var loaded = memberRepository.findById("mem_default").orElseThrow();

        assertThat(loaded.address(null).alias()).isEqualTo("office");
        assertThat(loaded.addresses().getFirst().defaultAddress()).isFalse();
    }

    /**
     * [PD-0001-R3] 이메일 조회는 대소문자를 구분한다. a@x.com과 A@x.com이 같은 사서함인지는 수신
     * 도메인이 정하는 것이므로 외부에서 단정하지 않는다. 접으면 다른 사서함의 주인이 가입하지 못한다.
     *
     * <p>저장소 조회만 잡는다. 가입이 중복으로 거절되는 경로는 보지 않는다.
     */
    @Test
    void distinguishesCaseInEmail() {
        var member = new Member("mem_email", "Mixed.Case@impati.dev", "Email Tester", HASH);
        memberRepository.save(member);

        assertThat(memberRepository.findByEmail("Mixed.Case@impati.dev")).isPresent();
        assertThat(memberRepository.findByEmail("mixed.case@impati.dev")).isEmpty();
        assertThat(memberRepository.findByEmail("MIXED.CASE@IMPATI.DEV")).isEmpty();
    }

    /** 대소문자가 다른 주소는 서로 다른 회원으로 가입할 수 있어야 한다. */
    @Test
    void allowsAddressesThatDifferOnlyByCase() {
        memberRepository.save(new Member("mem_lower", "twin@impati.dev", "Lower Twin", HASH));
        memberRepository.save(new Member("mem_upper", "Twin@impati.dev", "Upper Twin", HASH));

        assertThat(memberRepository.findByEmail("twin@impati.dev").orElseThrow().id()).isEqualTo("mem_lower");
        assertThat(memberRepository.findByEmail("Twin@impati.dev").orElseThrow().id()).isEqualTo("mem_upper");
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachAddressFieldToItsOwnColumn() {
        var member = new Member("mem_column", "column@impati.dev", "Column Tester", HASH);
        member.addAddress(address("home", "Seoul", true));
        memberRepository.save(member);

        var row = jdbc.queryForMap(
                "select alias, recipient, phone, line1, city, postal_code, default_address, address_no"
                        + " from member_addresses where member_id = ?",
                "mem_column"
        );
        assertThat(row.get("alias")).isEqualTo("home");
        assertThat(row.get("recipient")).isEqualTo("Demo Customer");
        assertThat(row.get("phone")).isEqualTo("010-0000-0000");
        assertThat(row.get("line1")).isEqualTo("123 Commerce Road");
        assertThat(row.get("city")).isEqualTo("Seoul");
        assertThat(row.get("postal_code")).isEqualTo("04524");
        assertThat(row.get("default_address")).isEqualTo(true);
        assertThat(row.get("address_no")).isEqualTo(0);
    }

    /** 가입 직후는 이메일 소유가 확인되지 않은 상태다. 확인 후 상태가 저장돼야 로그인이 가능해진다. */
    @Test
    void savesActivationAndPasswordHash() {
        var member = new Member("mem_activate", "activate@impati.dev", "Activate Tester", HASH);
        memberRepository.save(member);
        assertThat(memberRepository.findById("mem_activate").orElseThrow().isActive()).isFalse();

        member.activate();
        memberRepository.save(member);

        var loaded = memberRepository.findById("mem_activate").orElseThrow();
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.passwordHash().value()).isEqualTo(HASH.value());
        assertThat(jdbc.queryForObject(
                "select password_hash from members where id = ?", String.class, "mem_activate"))
                .isEqualTo(HASH.value());
        assertThat(jdbc.queryForObject(
                "select status from members where id = ?", String.class, "mem_activate"))
                .isEqualTo("ACTIVE");
    }

    @Test
    void returnsEmptyForUnknownMember() {
        assertThat(memberRepository.findById("mem_never_saved")).isEmpty();
    }

    private Address address(String alias, String city, boolean defaultAddress) {
        return new Address(alias, "Demo Customer", "010-0000-0000", "123 Commerce Road", city, "04524",
                defaultAddress);
    }
}
