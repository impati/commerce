package com.impati.commerce.order.application.port.out;

public interface ShipmentEventInbox {
    /** 처음 보는 사건이면 기록하고 true, 이미 처리한 사건이면 false를 반환한다. */
    boolean recordIfAbsent(String eventId);
}
