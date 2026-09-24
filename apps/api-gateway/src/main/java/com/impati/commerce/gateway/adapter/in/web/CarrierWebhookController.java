package com.impati.commerce.gateway.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CarrierEventRequest;
import com.impati.commerce.common.ApiContracts.CarrierEventResponse;
import com.impati.commerce.gateway.adapter.out.client.GatewayClients;
import com.impati.commerce.gateway.application.port.out.CarrierWebhookVerifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 브라우저 인증과 분리된 택배사 전용 공개 진입점. */
@RestController
@RequestMapping("/carrier/webhooks")
public class CarrierWebhookController {
    private final CarrierWebhookVerifier verifier;
    private final GatewayClients clients;

    public CarrierWebhookController(CarrierWebhookVerifier verifier, GatewayClients clients) {
        this.verifier = verifier;
        this.clients = clients;
    }

    @PostMapping("/events")
    CarrierEventResponse receive(
            @RequestHeader("X-Carrier-Event-Id") String eventId,
            @RequestHeader("X-Carrier-Timestamp") String timestamp,
            @RequestHeader("X-Carrier-Signature") String signature,
            @RequestBody String body
    ) {
        var event = verifier.verify(eventId, timestamp, signature, body);
        return clients.carrierEvent(new CarrierEventRequest(event.eventId(), event.carrierCode(),
                event.trackingNumber(), event.type(), event.occurredAt()));
    }
}
