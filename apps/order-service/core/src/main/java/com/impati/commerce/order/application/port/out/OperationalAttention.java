package com.impati.commerce.order.application.port.out;

/** 자동 복구가 안전하지 않은 체크아웃을 운영 절차에 전달하는 교체 가능한 경계. */
public interface OperationalAttention {
    void required(String orderId, String stage, String reason);
}
