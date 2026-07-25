package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.MemberRepository;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:member-repo;DB_CLOSE_DELAY=-1")
class JdbcMemberRepositoryTest {
    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsMemberWithAddresses() {
        var member = new Member("mem_round", "round@impati.dev", "Round Tester");
        member.addAddress(address("home", "Seoul", false));
        member.addAddress(address("office", "Busan", true));

        members.save(member);
        var loaded = members.findById("mem_round").orElseThrow();

        assertThat(loaded.email()).isEqualTo("round@impati.dev");
        assertThat(loaded.name()).isEqualTo("Round Tester");
        assertThat(loaded.status()).isEqualTo("ACTIVE");
        assertThat(loaded.addresses()).hasSize(2);
        assertThat(loaded.addresses().stream().map(Address::alias)).containsExactly("home", "office");
    }

    /**
     * 기본 배송지가 복원 후에도 유지돼야 한다. addAddress를 거쳐 복원하면 첫 주소가 강제로
     * 기본이 되므로 저장된 값과 달라진다. Member.restore가 그 경로를 우회하는 이유다.
     */
    @Test
    void keepsWhichAddressIsDefault() {
        var member = new Member("mem_default", "default@impati.dev", "Default Tester");
        member.addAddress(address("home", "Seoul", false));
        member.addAddress(address("office", "Busan", true));
        members.save(member);

        var loaded = members.findById("mem_default").orElseThrow();

        assertThat(loaded.address(null).alias()).isEqualTo("office");
        assertThat(loaded.addresses().getFirst().defaultAddress()).isFalse();
    }

    @Test
    void findsByEmailIgnoringCase() {
        var member = new Member("mem_email", "Mixed.Case@impati.dev", "Email Tester");
        members.save(member);

        assertThat(members.findByEmail("mixed.case@impati.dev")).isPresent();
        assertThat(members.findByEmail("MIXED.CASE@IMPATI.DEV")).isPresent();
        assertThat(members.findByEmail("other@impati.dev")).isEmpty();
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachAddressFieldToItsOwnColumn() {
        var member = new Member("mem_column", "column@impati.dev", "Column Tester");
        member.addAddress(address("home", "Seoul", true));
        members.save(member);

        var row = jdbc.queryForMap(
                "select alias, recipient, phone, line1, city, postal_code, default_address, address_no"
                        + " from member_addresses where member_id = ?",
                "mem_column"
        );
        assertThat(row.get("ALIAS")).isEqualTo("home");
        assertThat(row.get("RECIPIENT")).isEqualTo("Demo Customer");
        assertThat(row.get("PHONE")).isEqualTo("010-0000-0000");
        assertThat(row.get("LINE1")).isEqualTo("123 Commerce Road");
        assertThat(row.get("CITY")).isEqualTo("Seoul");
        assertThat(row.get("POSTAL_CODE")).isEqualTo("04524");
        assertThat(row.get("DEFAULT_ADDRESS")).isEqualTo(true);
        assertThat(row.get("ADDRESS_NO")).isEqualTo(0);
    }

    @Test
    void returnsEmptyForUnknownMember() {
        assertThat(members.findById("mem_never_saved")).isEmpty();
    }

    private Address address(String alias, String city, boolean defaultAddress) {
        return new Address(alias, "Demo Customer", "010-0000-0000", "123 Commerce Road", city, "04524",
                defaultAddress);
    }
}
