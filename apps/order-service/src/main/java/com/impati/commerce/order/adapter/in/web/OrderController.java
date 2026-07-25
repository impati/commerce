package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CheckoutRequest;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.order.application.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 API.
 *
 * <p>회원 신원은 {@code X-Member-Id} 헤더로 받는다. 주문 조회는 요청자 소유인지 확인한다 —
 * orderId만으로 조회되면 남의 주문 내역과 배송지가 노출된다.
 */
@RestController
@RequestMapping
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/checkouts")
    CheckoutResponse checkout(
            @RequestHeader("X-Member-Id") String memberId,
            @RequestBody CheckoutRequest request
    ) {
        return orders.checkout(memberId, request.paymentToken(), request.addressId());
    }

    @GetMapping("/orders/{orderId}")
    OrderResponse order(@RequestHeader("X-Member-Id") String memberId, @PathVariable String orderId) {
        return orders.getOwned(memberId, orderId);
    }

    /** shipping 흐름에서 게이트웨이가 부르는 내부 경로. 배송 완료 처리는 회원 요청이 아니다. */
    @PostMapping("/orders/{orderId}/delivered")
    OrderResponse delivered(@PathVariable String orderId) {
        return orders.markDelivered(orderId);
    }
}
