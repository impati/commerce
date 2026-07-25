package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CheckoutRequest;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.order.application.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/checkouts")
    CheckoutResponse checkout(@RequestBody CheckoutRequest request) {
        return orders.checkout(request.memberId(), request.paymentToken(), request.addressId());
    }

    @GetMapping("/orders/{orderId}")
    OrderResponse order(@PathVariable String orderId) {
        return orders.get(orderId);
    }

    @PostMapping("/orders/{orderId}/delivered")
    OrderResponse delivered(@PathVariable String orderId) {
        return orders.markDelivered(orderId);
    }
}

