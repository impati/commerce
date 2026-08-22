package com.impati.commerce.shipping.application.port.in;

/** 배송으로 할 수 있는 일. */
public interface ShippingUseCase {
    ShipmentDetails create(String orderId, String memberId, ShipmentAddress address);

    ShipmentDetails get(String shipmentId);

    ShipmentDetails ship(String shipmentId);

    /** 아직 나가지 않은 배송을 없앤다 (PD-0013-R5). 체크아웃 보상이 부른다 (PD-0012-R6). */
    ShipmentDetails cancel(String shipmentId);

    ShipmentDetails deliver(String shipmentId);
}
