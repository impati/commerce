package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.ApiContracts.ReturnShipmentResponse;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import java.util.Optional;

/** shipping-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface ShippingClient {
    ShipmentResponse createShipment(CreateShipmentRequest request);

    /** 아직 집하되지 않은 배송을 없앤다 (PD-0025-R5). 매입 전 실패를 되돌릴 때 부른다. */
    ShipmentResponse cancelShipment(String shipmentId);

    Optional<ShipmentResponse> shipmentForOrder(String orderId);

    ReturnShipmentResponse createReturnShipment(
            String returnId, String orderId, String memberId, AddressResponse pickupAddress);

    Optional<ReturnShipmentResponse> returnShipment(String returnId);

    ReturnShipmentResponse withdrawReturn(String returnShipmentId);

    ReturnShipmentResponse rescheduleReturn(String returnShipmentId, AddressResponse pickupAddress);
}
