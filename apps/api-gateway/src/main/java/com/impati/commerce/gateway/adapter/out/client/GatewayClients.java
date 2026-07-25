package com.impati.commerce.gateway.adapter.out.client;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class GatewayClients {
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final RestClient members;
    private final RestClient display;
    private final RestClient catalog;
    private final RestClient inventory;
    private final RestClient carts;
    private final RestClient orders;
    private final RestClient shipping;
    private final RestClient notifications;

    public GatewayClients(
            RestClient.Builder builder,
            @Value("${clients.member.url}") String memberUrl,
            @Value("${clients.display.url}") String displayUrl,
            @Value("${clients.catalog.url}") String catalogUrl,
            @Value("${clients.inventory.url}") String inventoryUrl,
            @Value("${clients.cart.url}") String cartUrl,
            @Value("${clients.order.url}") String orderUrl,
            @Value("${clients.shipping.url}") String shippingUrl,
            @Value("${clients.notification.url}") String notificationUrl
    ) {
        this.members = builder.clone().baseUrl(memberUrl).build();
        this.display = builder.clone().baseUrl(displayUrl).build();
        this.catalog = builder.clone().baseUrl(catalogUrl).build();
        this.inventory = builder.clone().baseUrl(inventoryUrl).build();
        this.carts = builder.clone().baseUrl(cartUrl).build();
        this.orders = builder.clone().baseUrl(orderUrl).build();
        this.shipping = builder.clone().baseUrl(shippingUrl).build();
        this.notifications = builder.clone().baseUrl(notificationUrl).build();
    }

    public DisplayHomeResponse home() {
        return display.get().uri("/display/home").retrieve().body(DisplayHomeResponse.class);
    }

    public List<ProductResponse> products(String category, String query) {
        return catalog.get()
                .uri(uriBuilder -> uriBuilder.path("/products")
                        .queryParamIfPresent("category", java.util.Optional.ofNullable(category))
                        .queryParamIfPresent("query", java.util.Optional.ofNullable(query))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public ProductResponse product(String productId) {
        return catalog.get().uri("/products/{productId}", productId).retrieve().body(ProductResponse.class);
    }

    public MemberResponse me(String memberId) {
        return members.get()
                .uri("/members/me")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(MemberResponse.class);
    }

    public MemberResponse register(RegisterMemberRequest request) {
        return members.post().uri("/members").body(request).retrieve().body(MemberResponse.class);
    }

    public MemberResponse verifyEmail(VerifyEmailRequest request) {
        return members.post().uri("/members/verifications").body(request).retrieve().body(MemberResponse.class);
    }

    public LoginResponse login(LoginRequest request) {
        return members.post().uri("/members/login").body(request).retrieve().body(LoginResponse.class);
    }

    public void logout(String token) {
        members.post()
                .uri("/members/logout")
                .body(new VerifyEmailRequest(token))
                .retrieve()
                .toBodilessEntity();
    }

    public void resendVerification(String memberId) {
        members.post()
                .uri("/members/verifications/resend")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .toBodilessEntity();
    }

    public AddressResponse addAddress(String memberId, AddAddressRequest request) {
        return members.post()
                .uri("/members/me/addresses")
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .body(AddressResponse.class);
    }

    public CartResponse cart(String memberId) {
        return carts.get()
                .uri("/carts")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(CartResponse.class);
    }

    public CartResponse addCartItem(String memberId, CartItemRequest request) {
        return carts.post()
                .uri("/carts/items")
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .body(CartResponse.class);
    }

    public CheckoutResponse checkout(String memberId, CheckoutRequest request) {
        return orders.post()
                .uri("/checkouts")
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .body(CheckoutResponse.class);
    }

    public OrderResponse order(String memberId, String orderId) {
        return orders.get()
                .uri("/orders/{orderId}", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(OrderResponse.class);
    }

    public OrderResponse markDelivered(String orderId) {
        return orders.post().uri("/orders/{orderId}/delivered", orderId).retrieve().body(OrderResponse.class);
    }

    public ShipmentResponse ship(String shipmentId) {
        return shipping.post().uri("/shipments/{shipmentId}/ship", shipmentId).retrieve().body(ShipmentResponse.class);
    }

    public ShipmentResponse deliver(String shipmentId) {
        return shipping.post().uri("/shipments/{shipmentId}/deliver", shipmentId).retrieve().body(ShipmentResponse.class);
    }

    public List<StockResponse> stock() {
        return inventory.get().uri("/stock").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public List<NotificationResponse> notifications(String memberId) {
        return notifications.get()
                .uri(uriBuilder -> uriBuilder.path("/notifications").queryParam("memberId", memberId).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}

