package com.impati.commerce.cart.application.port.in;

/** 장바구니로 할 수 있는 일. */
public interface CartUseCase {
    CartDetails addItem(String memberId, String skuId, int quantity);

    /** 조회는 저장소를 바꾸지 않는다. 장바구니가 없으면 빈 것을 응답만 하고 저장하지 않는다. */
    CartDetails get(String memberId);

    CartDetails clear(String memberId);

    /** 구매분을 한 번만 분리하며 같은 주문의 반복 요청에는 같은 사본을 반환한다 (PD-0018-R6). */
    CheckoutCartDetails checkout(String memberId, String orderId, long expectedVersion);
}
