package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;

/** shipping-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface ShippingClient {
    ShipmentResponse createShipment(CreateShipmentRequest request);

    /** 아직 나가지 않은 배송을 없앤다 (PD-0013-R5). 매입 전 실패를 되돌릴 때 부른다. */
    ShipmentResponse cancelShipment(String shipmentId);
}
