package com.impati.commerce.order.application.port.out;

/** 읽은 뒤 다른 트랜잭션이 같은 주문을 먼저 변경했음을 알리는 재시도 가능한 충돌. */
public class ConcurrentOrderModificationException extends RuntimeException {
    public ConcurrentOrderModificationException(String orderId) {
        super("order was concurrently modified: " + orderId);
    }
}
