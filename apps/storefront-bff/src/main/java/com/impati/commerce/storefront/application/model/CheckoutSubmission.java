package com.impati.commerce.storefront.application.model;

import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;

/** Order의 접수 결과. 각 도메인의 데이터는 그 도메인의 계약으로 보존한다. */
public record CheckoutSubmission(
        OrderResponse order,
        PaymentResponse payment,
        ShipmentResponse shipment,
        Acceptance acceptance
) {
    public enum Acceptance {
        PROCESSING,
        NEWLY_ACCEPTED,
        REPLAYED
    }
}
