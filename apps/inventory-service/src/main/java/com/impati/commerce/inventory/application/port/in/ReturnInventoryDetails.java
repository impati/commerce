package com.impati.commerce.inventory.application.port.in;

/** 검수 뒤 한 번 확정된 반품 재고 처리 결과. */
public record ReturnInventoryDetails(
        String returnId,
        String reservationId,
        String memberId,
        String disposition,
        String condition
) { }
