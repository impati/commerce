package com.impati.commerce.member.application;

import com.impati.commerce.member.domain.MemberModels.PasswordHash;

/**
 * 비밀번호 해싱 포트. 구현은 {@code adapter/out/security}에 둔다.
 *
 * <p>알고리즘을 포트 밖으로 드러내지 않는다. 해시 강도를 올리거나 알고리즘을 바꾸는 것은
 * 어댑터 교체로 끝나야 한다.
 */
public interface PasswordHasher {
    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);
}
