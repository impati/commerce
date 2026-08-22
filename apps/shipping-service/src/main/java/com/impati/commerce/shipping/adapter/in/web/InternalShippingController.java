package com.impati.commerce.shipping.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게이트웨이가 노출하지 않는 경로. 형제 서비스만 부른다 (ADR-0003).
 *
 * <p>배송 생성과 취소는 order-service의 checkout saga가 부른다. 생성 요청 본문에 {@code memberId}와
 * 주소가 들어가고, 취소는 실패한 체크아웃을 되돌리는 경로다 — 사용자가 부르는 기능이 아니다.
 * 단건 조회는 배송 식별자만 알면 남의 주소를 읽을 수 있으므로 같은 등급이다.
 */
@RestController
@RequestMapping("/internal/shipments")
public class InternalShippingController {
    private final ShippingUseCase shippingUseCase;

    public InternalShippingController(ShippingUseCase shippingUseCase) {
        this.shippingUseCase = shippingUseCase;
    }

    @PostMapping
    ShipmentResponse create(@RequestBody CreateShipmentRequest request) {
        return ShipmentResponseMapper.from(shippingUseCase.create(
                request.orderId(), request.memberId(), ShipmentResponseMapper.toAddress(request.address())));
    }

    @GetMapping("/{shipmentId}")
    ShipmentResponse get(@PathVariable String shipmentId) {
        return ShipmentResponseMapper.from(shippingUseCase.get(shipmentId));
    }

    @PostMapping("/{shipmentId}/cancel")
    ShipmentResponse cancel(@PathVariable String shipmentId) {
        return ShipmentResponseMapper.from(shippingUseCase.cancel(shipmentId));
    }
}
