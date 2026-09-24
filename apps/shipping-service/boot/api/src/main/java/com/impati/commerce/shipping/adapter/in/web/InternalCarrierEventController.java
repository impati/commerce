package com.impati.commerce.shipping.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CarrierEventRequest;
import com.impati.commerce.common.ApiContracts.CarrierEventResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.CarrierEventCommand;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import com.impati.commerce.shipping.domain.ShippingModels.CarrierEventType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 웹훅을 직접 노출하지 않고 API Gateway가 서명 검증한 택배 사건만 받는 내부 경로.
 */
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
                request.trackingNumber(), eventType(request.type()), request.occurredAt()));
        return new CarrierEventResponse(result.eventId(), result.shipmentId(), result.result(),
                result.shipmentStatus());
    }

    private static CarrierEventType eventType(String value) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation("carrier event type is required");
        }
        try {
            return CarrierEventType.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw DomainException.validation("unknown carrier event type");
        }
    }
}
