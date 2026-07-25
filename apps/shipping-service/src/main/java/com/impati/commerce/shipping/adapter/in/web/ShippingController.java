package com.impati.commerce.shipping.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.shipping.application.ShippingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shipments")
public class ShippingController {
    private final ShippingService shipping;

    public ShippingController(ShippingService shipping) {
        this.shipping = shipping;
    }

    @PostMapping
    ShipmentResponse create(@RequestBody CreateShipmentRequest request) {
        return shipping.create(request.orderId(), request.memberId(), request.address());
    }

    @GetMapping("/{shipmentId}")
    ShipmentResponse get(@PathVariable String shipmentId) {
        return shipping.get(shipmentId);
    }

    @PostMapping("/{shipmentId}/ship")
    ShipmentResponse ship(@PathVariable String shipmentId) {
        return shipping.ship(shipmentId);
    }

    @PostMapping("/{shipmentId}/deliver")
    ShipmentResponse deliver(@PathVariable String shipmentId) {
        return shipping.deliver(shipmentId);
    }
}

