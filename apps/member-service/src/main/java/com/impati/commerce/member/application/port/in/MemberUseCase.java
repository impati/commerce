package com.impati.commerce.member.application.port.in;

/** 회원 프로필로 할 수 있는 일. */
public interface MemberUseCase {
    MemberAddress addAddress(String memberId, NewAddress address);

    MemberDetails get(String memberId);
}
