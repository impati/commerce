package com.impati.commerce.order.application;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;

/** shipping-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface ShippingClient {
    ShipmentResponse createShipment(CreateShipmentRequest request);
}
