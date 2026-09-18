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
