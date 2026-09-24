package com.impati.commerce.shipping.application.port.in;

/**
 * 배송지. 유스케이스가 받고 돌려주는 형태다.
 *
 * <p>서비스 간 계약({@code AddressResponse})을 쓰지 않는다. 유스케이스의 입출력은 서비스
 * 사이에서 오가는 것이 아니므로, 어댑터가 자기 표현에서 이 타입으로 옮긴다.
 */
public record ShipmentAddress(
        String id,
        String alias,
        String recipient,
        String phone,
        String line1,
        String city,
        String postalCode,
        boolean defaultAddress
) {
}
