package com.impati.commerce.member.application.component;

import com.impati.commerce.member.application.port.in.MemberAddress;
import com.impati.commerce.member.application.port.in.MemberDetails;
import com.impati.commerce.member.application.port.in.NewAddress;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;

/**
 * 도메인 모델과 유스케이스 입출력을 잇는다. 도메인은 그 타입들을 모른다.
 *
 * <p>package-private인 것이 의도다. 어댑터가 볼 수 있으면 도메인 객체를 손에 넣어야 부를 수
 * 있고, 그때부터 도메인이 어댑터로 새기 시작한다.
 */
final class MemberMapper {
    private MemberMapper() {
    }

    static Address toAddress(NewAddress address) {
        return new Address(
                address.alias(),
                address.recipient(),
                address.phone(),
                address.line1(),
                address.city(),
                address.postalCode(),
                address.defaultAddress()
        );
    }

    static MemberAddress toDetails(Address address) {
        return new MemberAddress(
                address.id(),
                address.alias(),
                address.recipient(),
                address.phone(),
                address.line1(),
                address.city(),
                address.postalCode(),
                address.defaultAddress()
        );
    }

    static MemberDetails toDetails(Member member) {
        return new MemberDetails(
                member.id(),
                member.email(),
                member.name(),
                member.status(),
                member.addresses().stream().map(MemberMapper::toDetails).toList(),
                member.addressBookVersion()
        );
    }
}
