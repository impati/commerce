package com.impati.commerce.member.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import com.impati.commerce.member.domain.MemberModels.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressBookTest {
    private final Member member = new Member("m@example.test", "Member", new PasswordHash("hash"));

    /** [PD-0022-R2, PD-0022-R3] 기본 지정과 기본 주소 삭제를 등록 순서로 조정한다. */
    @Test
    void maintainsOneDefaultAndCanRemoveTheLastAddress() {
        var home = address("home", false);
        var office = address("office", false);
        member.addAddress(home);
        member.addAddress(office);
        assertThat(home.defaultAddress()).isTrue();
        member.setDefaultAddress(office.id(), 2);
        assertThat(home.defaultAddress()).isFalse();
        assertThat(office.defaultAddress()).isTrue();
        member.removeAddress(office.id(), 3);
        assertThat(member.address(null).id()).isEqualTo(home.id());
        member.removeAddress(home.id(), 4);
        assertThat(member.addresses()).isEmpty();
        assertThatThrownBy(() -> member.address(null)).isInstanceOf(DomainException.class);
    }

    /** [PD-0022-R4] 다른 주소의 변경도 같은 주소록의 오래된 관리 요청을 거절한다. */
    @Test
    void rejectsStaleUpdatesRemovalsAndDefaultChanges() {
        var home = address("home", false);
        member.addAddress(home);
        member.addAddress(address("office", false));
        assertThatThrownBy(() -> member.updateAddress(home.id(), address("changed", false), 1))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("address_book_changed"));
        assertThatThrownBy(() -> member.removeAddress(home.id(), 1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> member.setDefaultAddress(home.id(), 1)).isInstanceOf(DomainException.class);
        assertThat(member.addresses()).hasSize(2);
        assertThat(member.addressBookVersion()).isEqualTo(2);
    }

    /** [PD-0022-R1] 없는 주소와 남의 주소를 관리 명령으로 선택할 수 없다. */
    @Test
    void rejectsAddressesOutsideTheBookWithoutChangingIt() {
        member.addAddress(address("home", false));
        assertThatThrownBy(() -> member.updateAddress("foreign", address("changed", false), 1))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> member.removeAddress("foreign", 1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> member.setDefaultAddress("foreign", 1)).isInstanceOf(DomainException.class);
        assertThat(member.addressBookVersion()).isEqualTo(1);
    }

    /** [PD-0022-R2] 편집은 주소 ID와 등록 위치 및 기본 지정을 보존한다. */
    @Test
    void editsFieldsWithoutChangingIdentityOrDefault() {
        var home = address("home", false);
        member.addAddress(home);
        member.updateAddress(home.id(), new Address("renamed", "New recipient", "new-phone",
                "New road", "Busan", "99999", false), 1);
        var updated = member.address(null);
        assertThat(updated.id()).isEqualTo(home.id());
        assertThat(updated.alias()).isEqualTo("renamed");
        assertThat(updated.recipient()).isEqualTo("New recipient");
        assertThat(updated.phone()).isEqualTo("new-phone");
        assertThat(updated.line1()).isEqualTo("New road");
        assertThat(updated.city()).isEqualTo("Busan");
        assertThat(updated.postalCode()).isEqualTo("99999");
        assertThat(updated.defaultAddress()).isTrue();
    }

    /** [PD-0022-R8] 빈 값과 DB 길이 경계를 검증하며 보충 문자도 문자 단위로 센다. */
    @Test
    void validatesAllInputFieldsAndLengthBoundaries() {
        var values = new String[]{"alias", "recipient", "phone", "road", "city", "postal"};
        var limits = new int[]{64, 128, 64, 255, 128, 32};
        for (var index = 0; index < values.length; index++) {
            var blank = values.clone();
            blank[index] = "   ";
            assertThatThrownBy(() -> from(blank)).isInstanceOf(DomainException.class);
            var boundary = values.clone();
            boundary[index] = "x".repeat(limits[index]);
            from(boundary);
            boundary[index] += "x";
            assertThatThrownBy(() -> from(boundary)).isInstanceOf(DomainException.class);
        }
        from(new String[]{"😀".repeat(64), "recipient", "phone", "road", "city", "postal"});
    }

    private static Address from(String[] fields) {
        return new Address(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], false);
    }

    private static Address address(String alias, boolean defaultAddress) {
        return new Address(alias, "Member", "010", "Road", "Seoul", "00000", defaultAddress);
    }
}
