package com.impati.commerce.member.application.component;

import com.impati.commerce.member.application.port.in.MemberAddress;
import com.impati.commerce.member.application.port.in.MemberUseCase;
import com.impati.commerce.member.application.port.in.NewAddress;
import com.impati.commerce.member.application.port.in.RegistrationUseCase;
import com.impati.commerce.member.application.port.in.SessionUseCase;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.member.application.port.out.SecureTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 배송지 추가를 검증한다.
 *
 * <p>핵심은 {@link #mapsEachRequestFieldToItsOwnField}다. alias·recipient·phone·line1·city·postalCode는
 * 전부 String이라 두 필드가 뒤바뀌어도 컴파일러가 잡지 못한다. 값을 필드마다 다르게 넣어
 * 어긋남이 드러나게 한다. 같은 종류의 실수를 JDBC 위치 바인딩에서 이미 겪었다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-address;DB_CLOSE_DELAY=-1",
        // 발송기를 멈춘다. 여기서 가입시킨 회원의 아웃박스 항목을 발송기가 집어 상태를 바꾼다.
        "member.verification-mail-dispatch-interval=3600000"
})
class MemberAddressTest {
    @Autowired
    private MemberUseCase memberUseCase;

    @Autowired
    private RegistrationUseCase registrationUseCase;

    @MockBean
    private NotificationClient notificationClient;

    @Test
    void mapsEachRequestFieldToItsOwnField() {
        var member = registrationUseCase.register("address@impati.dev", "Address", "address-pw12");

        var address = memberUseCase.addAddress(member.id(), new NewAddress(
                "alias-value",
                "recipient-value",
                "phone-value",
                "line1-value",
                "city-value",
                "postal-value",
                true
        ));

        assertThat(address.alias()).isEqualTo("alias-value");
        assertThat(address.recipient()).isEqualTo("recipient-value");
        assertThat(address.phone()).isEqualTo("phone-value");
        assertThat(address.line1()).isEqualTo("line1-value");
        assertThat(address.city()).isEqualTo("city-value");
        assertThat(address.postalCode()).isEqualTo("postal-value");
        assertThat(address.defaultAddress()).isTrue();
    }

    /** 추가한 배송지는 회원 조회에도 보인다. */
    @Test
    void addedAddressIsVisibleOnTheMember() {
        var member = registrationUseCase.register("visible@impati.dev", "Visible", "visible-pw12");

        memberUseCase.addAddress(member.id(), request("home"));
        memberUseCase.addAddress(member.id(), request("office"));

        assertThat(memberUseCase.get(member.id()).addresses())
                .extracting(MemberAddress::alias)
                .containsExactly("home", "office");
    }

    /** 없는 회원에게는 배송지를 붙일 수 없다. */
    @Test
    void rejectsUnknownMember() {
        assertThatThrownBy(() -> memberUseCase.addAddress("mem_never_saved", request("home")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("member not found");
    }

    private static NewAddress request(String alias) {
        return new NewAddress(
                alias, "Recipient", "010-0000-0000", "123 Commerce Road", "Seoul", "04524", false);
    }
}
