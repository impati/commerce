package com.impati.commerce.order.application.port.in;

/** 주문으로 할 수 있는 일. */
public interface OrderUseCase {
    /** 체크아웃 saga를 조율한다. 순서와 보상 범위는 PD-0017이 정한다. */
    CheckoutResult checkout(String memberId, String idempotencyKey, String paymentToken, String addressId);

    /** 요청자 소유의 주문만 돌려준다. 없는 주문과 남의 주문을 같은 응답으로 거절한다 (PD-0003-R8). */
    OrderDetails getOwned(String memberId, String orderId);

    OrderDetails markDelivered(String orderId);
}
