package com.impati.commerce.shipping.application.port.in;

/**
 * 배송으로 할 수 있는 일.
 */
public interface ShippingUseCase {

    ShipmentDetails create(String orderId, String memberId, ShipmentAddress address);

    ShipmentDetails get(String shipmentId);

    ShipmentDetails getForOrder(String orderId);

    ShipmentDetails completePacking(String shipmentId);

    ShipmentDetails createReturnShipment(
            String returnId,
            String orderId,
            String memberId,
            ShipmentAddress pickupAddress
    );

    ShipmentDetails getForReturn(String returnId);

    ShipmentDetails withdrawReturn(String returnShipmentId);

    ShipmentDetails rescheduleReturnPickup(String returnShipmentId, ShipmentAddress pickupAddress);

    /**
     * 택배사 집하 전 배송을 없앤다 (PD-0025-R5). 체크아웃 보상이 부른다 (PD-0017-R7).
     */
    ShipmentDetails cancel(String shipmentId);

    /**
     * API Gateway에서 인증된 택배사 사건을 멱등하게 기록하고 배송 상태에 반영한다.
     * 같은 사건 ID와 같은 정규화 내용은 {@code DUPLICATE}, 다른 내용은 {@code CONFLICT}로 반환한다.
     */
    CarrierEventResult receive(CarrierEventCommand command);
}
