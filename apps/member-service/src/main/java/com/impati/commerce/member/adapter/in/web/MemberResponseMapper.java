package com.impati.commerce.member.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.LoginResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.AccessTokenResponse;
import com.impati.commerce.member.application.port.in.IssuedSession;
import com.impati.commerce.member.application.port.in.MemberAddress;
import com.impati.commerce.member.application.port.in.MemberDetails;
import com.impati.commerce.member.application.port.in.NewAddress;
import com.impati.commerce.member.application.port.in.IssuedAccessToken;

/**
 * 서비스 간 HTTP 계약과 유스케이스 입출력을 잇는다.
 *
 * <p>계약이 <b>서비스 간</b>의 것이므로 어댑터가 안다. 응용 계층이 알면 인바운드 어댑터가
 * 늘어날 때마다 응용이 바뀐다 — 이 서비스는 컨트롤러가 넷이고 시드도 응용을 부른다.
 */
final class MemberResponseMapper {
    private MemberResponseMapper() {
    }

    static NewAddress toNewAddress(AddAddressRequest request) {
        return new NewAddress(
                request.alias(),
                request.recipient(),
                request.phone(),
                request.line1(),
                request.city(),
                request.postalCode(),
                request.defaultAddress()
        );
    }

    static AddressResponse from(MemberAddress address) {
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

    static MemberResponse from(MemberDetails member) {
        return new MemberResponse(
                member.id(),
                member.email(),
                member.name(),
                member.status(),
                member.addresses().stream().map(MemberResponseMapper::from).toList(),
                member.addressBookVersion()
        );
    }

    static LoginResponse from(IssuedSession session) {
        return new LoginResponse(
                session.sessionToken(),
                session.sessionExpiresAt(),
                session.accessToken(),
                session.accessTokenExpiresAt()
        );
    }

    static AccessTokenResponse from(IssuedAccessToken issued) {
        return new AccessTokenResponse(issued.accessToken(), issued.accessTokenExpiresAt());
    }
}
