package com.impati.commerce.member.application.port.in;

import java.util.List;

/**
 * 회원의 현재 상태.
 *
 * <p>서비스 간 계약({@code ApiContracts})을 돌려주지 않는다. 유스케이스의 반환값은 서비스
 * 사이에서 오가는 것이 아니므로 인바운드 어댑터가 각자 자기 표현으로 옮긴다.
 */
public record MemberDetails(String id, String email, String name, String status, List<MemberAddress> addresses) {
}
