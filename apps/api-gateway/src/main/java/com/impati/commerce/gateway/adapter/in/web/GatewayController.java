package com.impati.commerce.gateway.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutRequest;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.RegisterMemberRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.gateway.adapter.out.client.GatewayClients;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class GatewayController {
    private final GatewayClients clients;

    public GatewayController(GatewayClients clients) {
        this.clients = clients;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/display/home")
    DisplayHomeResponse home() {
        return clients.home();
    }

    @GetMapping("/products")
    List<ProductResponse> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query
    ) {
        return clients.products(category, query);
    }

    @GetMapping("/products/{productId}")
    ProductResponse product(@PathVariable String productId) {
        return clients.product(productId);
    }

    @GetMapping("/members")
    List<MemberResponse> members() {
        return clients.members();
    }

    @PostMapping("/members")
    MemberResponse register(@RequestBody RegisterMemberRequest request) {
        return clients.register(request);
    }

    @GetMapping("/members/{memberId}")
    MemberResponse member(@PathVariable String memberId) {
        return clients.member(memberId);
    }

    @PostMapping("/members/{memberId}/addresses")
    AddressResponse addAddress(@PathVariable String memberId, @RequestBody AddAddressRequest request) {
        return clients.addAddress(memberId, request);
    }

    @GetMapping("/cart/{memberId}")
    CartResponse cart(@PathVariable String memberId) {
        return clients.cart(memberId);
    }

    @PostMapping("/cart/{memberId}/items")
    CartResponse addCartItem(@PathVariable String memberId, @RequestBody CartItemRequest request) {
        return clients.addCartItem(memberId, request);
    }

    @PostMapping("/checkout")
    CheckoutResponse checkout(@RequestBody CheckoutRequest request) {
        return clients.checkout(request);
    }

    @GetMapping("/orders/{orderId}")
    OrderResponse order(@PathVariable String orderId) {
        return clients.order(orderId);
    }

    @PostMapping("/shipments/{shipmentId}/ship")
    ShipmentResponse ship(@PathVariable String shipmentId) {
        return clients.ship(shipmentId);
    }

    @PostMapping("/shipments/{shipmentId}/deliver")
    Map<String, Object> deliver(@PathVariable String shipmentId) {
        var shipment = clients.deliver(shipmentId);
        var order = clients.markDelivered(shipment.orderId());
        return Map.of("shipment", shipment, "order", order);
    }

    @GetMapping("/inventory")
    List<StockResponse> stock() {
        return clients.stock();
    }

    @GetMapping("/notifications")
    List<NotificationResponse> notifications() {
        return clients.notifications();
    }
}

