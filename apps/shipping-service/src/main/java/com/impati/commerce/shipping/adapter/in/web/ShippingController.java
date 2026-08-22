package com.impati.commerce.shipping.adapter.in.web;

import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배송 진행 API.
 *
 * <p>발송·배달 전이는 데모에서 사용자가 직접 눌러 흐름을 확인하는 용도로 게이트웨이가 노출한다.
 * 실제 운영이라면 배송사 웹훅이나 운영 도구가 부르는 자리다.
 */
@RestController
@RequestMapping("/shipments")
public class ShippingController {
    private final ShippingUseCase shipping;

    public ShippingController(ShippingUseCase shipping) {
        this.shipping = shipping;
    }

    @PostMapping("/{shipmentId}/ship")
    ShipmentResponse ship(@PathVariable String shipmentId) {
        return ShipmentResponseMapper.from(shipping.ship(shipmentId));
    }

    @PostMapping("/{shipmentId}/deliver")
    ShipmentResponse deliver(@PathVariable String shipmentId) {
        return ShipmentResponseMapper.from(shipping.deliver(shipmentId));
    }
}
