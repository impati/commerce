package com.impati.commerce.shipping.application.port.in;

/** 배송의 현재 상태. */
public record ShipmentDetails(
        String id,
        String kind,
        String returnId,
        String orderId,
        String memberId,
        ShipmentAddress address,
        String status,
        String carrierCode,
        String carrierName,
        String trackingNumber
) {
}
