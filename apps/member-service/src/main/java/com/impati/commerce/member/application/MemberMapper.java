package com.impati.commerce.member.application;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;

/**
 * 도메인 모델과 서비스 간 계약(ApiContracts)을 잇는다. 도메인은 계약을 모른다.
 */
final class MemberMapper {
    private MemberMapper() {
    }

    static AddressResponse toResponse(Address address) {
        return new AddressResponse(
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

    static MemberResponse toResponse(Member member) {
        return new MemberResponse(
                member.id(),
                member.email(),
                member.name(),
                member.status(),
                member.addresses().stream().map(MemberMapper::toResponse).toList()
        );
    }
}
