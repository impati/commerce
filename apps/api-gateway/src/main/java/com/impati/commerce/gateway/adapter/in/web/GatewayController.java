package com.impati.commerce.gateway.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AccessTokenResponse;
import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CheckoutRequest;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.common.ApiContracts.LoginRequest;
import com.impati.commerce.common.ApiContracts.LoginResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.RegisterMemberRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.ApiContracts.VerifyEmailRequest;
import com.impati.commerce.gateway.adapter.out.client.GatewayClients;
import com.impati.commerce.gateway.support.MemberIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 외부 진입점.
 *
 * <p>퍼블릭 경로에 {@code memberId}를 두지 않는다. 세션 토큰을 검증해 얻은 신원을
 * {@code X-Member-Id}로 하위 서비스에 넘긴다. 토큰 검증 지식은 이 계층에만 있다.
 *
 * <p>전체 회원 목록 조회는 제거했다. 인증 없이 모든 회원의 이메일을 내주는 경로였다. 관리자용이
 * 필요하면 별도 경로와 권한으로 분리해야 한다.
 */
@RestController
public class GatewayController {
    private final GatewayClients clients;
    private final MemberIdentity identity;

    public GatewayController(GatewayClients clients, MemberIdentity identity) {
        this.clients = clients;
        this.identity = identity;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // --- 인증 없이 열린 경로 ---

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

    @GetMapping("/inventory")
    List<StockResponse> stock() {
        return clients.stock();
    }

    @PostMapping("/members")
    MemberResponse register(@RequestBody RegisterMemberRequest request) {
        return clients.register(request);
    }

    @PostMapping("/members/verifications")
    MemberResponse verifyEmail(@RequestBody VerifyEmailRequest request) {
        return clients.verifyEmail(request);
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        return clients.login(request);
    }

    /**
     * 접근 토큰을 갱신한다.
     *
     * <p>세션 토큰을 받으므로 {@code identity.require}를 쓰지 않는다 — 그쪽은 접근 토큰을 본다.
     * 만료된 접근 토큰으로 401을 받은 클라이언트가 이 경로로 온다.
     */
    @PostMapping("/sessions/refresh")
    AccessTokenResponse refresh(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return clients.refresh(identity.bearerToken(authorization));
    }

    // --- 세션이 필요한 경로 ---

    @PostMapping("/logout")
    void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        clients.logout(identity.bearerToken(authorization));
    }

    @GetMapping("/me")
    MemberResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return clients.me(identity.require(authorization));
    }

    @PostMapping("/me/addresses")
    AddressResponse addAddress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AddAddressRequest request
    ) {
        return clients.addAddress(identity.require(authorization), request);
    }

    @PostMapping("/me/verifications/resend")
    void resendVerification(@RequestHeader(value = "Authorization", required = false) String authorization) {
        clients.resendVerification(identity.require(authorization));
    }

    @GetMapping("/cart")
    CartResponse cart(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return clients.cart(identity.require(authorization));
    }

    @PostMapping("/cart/items")
    CartResponse addCartItem(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CartItemRequest request
    ) {
        return clients.addCartItem(identity.require(authorization), request);
    }

    @PostMapping("/checkout")
    CheckoutResponse checkout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CheckoutRequest request
    ) {
        return clients.checkout(identity.require(authorization), request);
    }

    @GetMapping("/orders/{orderId}")
    OrderResponse order(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String orderId
    ) {
        return clients.order(identity.require(authorization), orderId);
    }

    @GetMapping("/notifications")
    List<NotificationResponse> notifications(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return clients.notifications(identity.require(authorization));
    }

    @PostMapping("/shipments/{shipmentId}/ship")
    ShipmentResponse ship(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String shipmentId
    ) {
        identity.require(authorization);
        return clients.ship(shipmentId);
    }

    @PostMapping("/shipments/{shipmentId}/deliver")
    Map<String, Object> deliver(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String shipmentId
    ) {
        identity.require(authorization);
        var shipment = clients.deliver(shipmentId);
        var order = clients.markDelivered(shipment.orderId());
        return Map.of("shipment", shipment, "order", order);
    }
}
