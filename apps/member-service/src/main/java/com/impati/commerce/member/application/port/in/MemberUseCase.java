package com.impati.commerce.member.application.port.in;

/** 회원 프로필로 할 수 있는 일. */
public interface MemberUseCase {
    MemberAddress addAddress(String memberId, NewAddress address, long expectedVersion);

    MemberDetails updateAddress(String memberId, String addressId, NewAddress address, long expectedVersion);

    MemberDetails removeAddress(String memberId, String addressId, long expectedVersion);

    MemberDetails setDefaultAddress(String memberId, String addressId, long expectedVersion);

    MemberDetails get(String memberId);
}
