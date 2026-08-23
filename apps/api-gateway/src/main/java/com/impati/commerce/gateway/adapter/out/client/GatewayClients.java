package com.impati.commerce.gateway.adapter.out.client;

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
import com.impati.commerce.common.ApiContracts.SessionTokenRequest;
import com.impati.commerce.common.ApiContracts.VerifyEmailRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import com.impati.commerce.common.DomainException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
            RestClient memberRestClient,
            RestClient displayRestClient,
            RestClient catalogRestClient,
            RestClient inventoryRestClient,
            RestClient cartRestClient,
            RestClient orderRestClient,
            RestClient shippingRestClient,
            RestClient notificationRestClient
    ) {
        this.members = memberRestClient;
        this.display = displayRestClient;
        this.catalog = catalogRestClient;
        this.inventory = inventoryRestClient;
        this.carts = cartRestClient;
        this.orders = orderRestClient;
        this.shipping = shippingRestClient;
        this.notifications = notificationRestClient;
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

    /**
     * 세션 토큰을 새 접근 토큰으로 바꾼다. 인증 경로 중 member-service를 부르는 유일한 곳이다.
     *
     * <p>member-service는 쓸 수 없는 세션을 404로 답한다. 그대로 흘려보내면 호출자에게 "그런
     * 경로가 없다"로 읽히므로 인증 실패로 옮긴다. 이유를 구분하지 않는 것은 정해진 규칙이다
     * (PD-0014-R7). 그 밖의 응답과 전송 실패는 여기서 다루지 않는다 — BL-0036·BL-0037이
     * 게이트웨이의 프록시 호출 전체를 함께 볼 대상이다.
     */
    public AccessTokenResponse refresh(String sessionToken) {
        try {
            return members.post()
                    .uri("/internal/members/sessions/refresh")
                    .body(new SessionTokenRequest(sessionToken))
                    .retrieve()
                    .body(AccessTokenResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new DomainException("unauthorized", "authentication is required", 401);
            }
            throw exception;
        }
    }

    public void logout(String token) {
        members.post()
                .uri("/members/logout")
                .body(new SessionTokenRequest(token))
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

