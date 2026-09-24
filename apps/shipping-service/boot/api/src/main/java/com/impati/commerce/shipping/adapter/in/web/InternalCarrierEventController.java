package com.impati.commerce.shipping.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CarrierEventRequest;
import com.impati.commerce.common.ApiContracts.CarrierEventResponse;
import com.impati.commerce.shipping.application.port.in.CarrierEventCommand;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API Gateway가 검증·정규화한 택배 사건만 받는 내부 경로. */
@RestController
@RequestMapping("/internal/carrier-events")
public class InternalCarrierEventController {
    private final ShippingUseCase shippingUseCase;

    public InternalCarrierEventController(ShippingUseCase shippingUseCase) {
        this.shippingUseCase = shippingUseCase;
    }

    @PostMapping
    CarrierEventResponse receive(@RequestBody CarrierEventRequest request) {
        var result = shippingUseCase.receive(new CarrierEventCommand(request.eventId(), request.carrierCode(),
                request.trackingNumber(), request.type(), request.occurredAt()));
        return new CarrierEventResponse(result.eventId(), result.shipmentId(), result.result(),
                result.shipmentStatus());
    }
}
