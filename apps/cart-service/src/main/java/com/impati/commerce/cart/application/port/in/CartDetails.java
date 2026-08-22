package com.impati.commerce.cart.application.port.in;

import java.util.List;

/**
 * 장바구니의 현재 상태.
 *
 * <p>서비스 간 계약({@code ApiContracts})을 돌려주지 않는 이유는 유스케이스의 반환값이 서비스
 * 간에 주고받는 것이 아니기 때문이다. 인바운드 어댑터가 각자 자기 표현으로 옮긴다.
 */
public record CartDetails(String memberId, List<CartLine> lines) {
}
