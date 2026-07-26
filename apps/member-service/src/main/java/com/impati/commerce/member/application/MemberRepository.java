package com.impati.commerce.member.application;

import com.impati.commerce.member.domain.MemberModels.Member;

import java.util.Optional;

/**
 * 회원 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 */
public interface MemberRepository {
    void save(Member member);

    Optional<Member> findById(String memberId);

    /** 이메일은 대소문자를 구분한다. 로컬부의 해석 권한이 수신 도메인에 있으므로 우리가 접지 않는다. */
    Optional<Member> findByEmail(String email);
}
