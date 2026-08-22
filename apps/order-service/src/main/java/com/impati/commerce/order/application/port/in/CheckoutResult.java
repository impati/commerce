package com.impati.commerce.order.application.port.in;

import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;

/**
 * 체크아웃이 성립한 결과.
 *
 * <p>결제와 배송은 다른 서비스가 소유한 개념이라 그쪽 계약을 그대로 담는다. 이 서비스가
 * 만들어내는 것은 주문뿐이고, 그것만 자기 타입으로 표현한다.
 */
public record CheckoutResult(OrderDetails order, PaymentResponse payment, ShipmentResponse shipment) {
}
