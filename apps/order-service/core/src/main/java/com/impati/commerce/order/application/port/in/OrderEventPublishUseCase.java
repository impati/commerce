package com.impati.commerce.order.application.port.in;

/** 아직 발행되지 않은 주문 사건을 내보낸다. */
public interface OrderEventPublishUseCase {
    /** 이번 주기에 발행이 확정된 건수를 돌려준다. */
    int publishPending();
}
