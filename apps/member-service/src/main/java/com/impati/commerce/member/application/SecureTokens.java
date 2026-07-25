package com.impati.commerce.member.application;

/**
 * 토큰 생성과 해싱 포트. 구현은 {@code adapter/out/security}에 둔다.
 *
 * <p>원문 토큰은 만들어서 한 번 내보내고 저장하지 않는다. 저장은 해시만 한다. DB가 유출됐을 때
 * 평문 토큰이 그대로 계정 접근 수단이 되는 것을 막는 것이 목적이다.
 */
public interface SecureTokens {
    /** 추측 불가능한 원문 토큰을 만든다. */
    String newToken();

    /** 저장·조회에 쓰는 해시. 같은 입력에 같은 결과가 나와야 조회가 가능하다. */
    String hash(String rawToken);
}
