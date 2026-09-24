package com.impati.commerce.shipping.application.port.in;

/** 배송으로 할 수 있는 일. */
public interface ShippingUseCase {
    ShipmentDetails create(String orderId, String memberId, ShipmentAddress address);

    ShipmentDetails get(String shipmentId);

    ShipmentDetails getForOrder(String orderId);

    ShipmentDetails completePacking(String shipmentId);

    /** 택배사 집하 전 배송을 없앤다 (PD-0025-R5). 체크아웃 보상이 부른다 (PD-0017-R7). */
    ShipmentDetails cancel(String shipmentId);

    CarrierEventResult receive(CarrierEventCommand command);
}
