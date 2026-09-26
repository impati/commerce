package com.impati.commerce.gateway.adapter.out.client;

import com.impati.commerce.common.ApiContracts.AccessTokenResponse;
import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.UpdateAddressRequest;
import com.impati.commerce.common.ApiContracts.SetDefaultAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CartItemRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CarrierEventRequest;
import com.impati.commerce.common.ApiContracts.CarrierEventResponse;
import com.impati.commerce.common.ApiContracts.ChangeCartQuantityRequest;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.common.ApiContracts.LoginRequest;
import com.impati.commerce.common.ApiContracts.LoginResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.common.ApiContracts.OrderDetailResponse;
import com.impati.commerce.common.ApiContracts.OrderCancellationResponse;
import com.impati.commerce.common.ApiContracts.OrderPageResponse;
import com.impati.commerce.common.ApiContracts.OrderReturnResponse;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.RegisterMemberRequest;
import com.impati.commerce.common.ApiContracts.RequestOrderReturn;
import com.impati.commerce.common.ApiContracts.RescheduleReturnPickupRequest;
import com.impati.commerce.common.ApiContracts.SessionTokenRequest;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.ApiContracts.StorefrontCartResponse;
import com.impati.commerce.common.ApiContracts.VerifyEmailRequest;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.gateway.support.MemberServiceAvailability;
import com.impati.commerce.http.RestClientFactory;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GatewayClients {

    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final MemberServiceAvailability memberServiceAvailability;

    private final RestClient members;
    private final RestClient display;
    private final RestClient catalog;
    private final RestClient inventory;
    private final RestClient storefront;
    private final RestClient cartPage;
    private final RestClient orders;
    private final RestClient shipping;
    private final RestClient notifications;

    public GatewayClients(
            MemberServiceAvailability memberServiceAvailability,
            RestClientFactory restClients,
            @Value("${clients.member.url}") String memberBaseUrl,
            @Value("${clients.display.url}") String displayBaseUrl,
            @Value("${clients.catalog.url}") String catalogBaseUrl,
            @Value("${clients.inventory.url}") String inventoryBaseUrl,
            @Value("${clients.storefront.url}") String storefrontBaseUrl,
            @Value("${clients.storefront.cart-read-timeout:PT7S}") Duration cartReadTimeout,
            @Value("${clients.order.url}") String orderBaseUrl,
            @Value("${clients.shipping.url}") String shippingBaseUrl,
            @Value("${clients.notification.url}") String notificationBaseUrl
    ) {
        this.memberServiceAvailability = memberServiceAvailability;
        this.members = restClients.forBaseUrl(memberBaseUrl);
        this.display = restClients.forBaseUrl(displayBaseUrl);
        this.catalog = restClients.forBaseUrl(catalogBaseUrl);
        this.inventory = restClients.forBaseUrl(inventoryBaseUrl);
        this.storefront = restClients.forBaseUrl(storefrontBaseUrl);
        this.cartPage = restClients.forBaseUrl(storefrontBaseUrl, cartReadTimeout);
        this.orders = restClients.forBaseUrl(orderBaseUrl);
        this.shipping = restClients.forBaseUrl(shippingBaseUrl);
        this.notifications = restClients.forBaseUrl(notificationBaseUrl);
    }

    public DisplayHomeResponse home() {
        return display.get().uri("/display/home").retrieve().body(DisplayHomeResponse.class);
    }

    public List<ProductResponse> products(String category, String query) {
        return catalog.get()
                .uri(uriBuilder -> uriBuilder.path("/products")
                        .queryParamIfPresent("category", Optional.ofNullable(category))
                        .queryParamIfPresent("query", Optional.ofNullable(query))
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
     * (PD-0014-R7). 그 밖의 응답 변환은 여기서 다루지 않는다 — BL-0036·BL-0037이 게이트웨이의
     * 프록시 호출 전체를 함께 볼 대상이다.
     *
     * <p>여기가 인증 경로에서 member-service에 닿는 유일한 곳이므로 장애 판정의 신호도 여기서
     * 나온다. <b>404는 실패로 세지 않는다</b> — 세션이 쓸 수 없다는 것은 member-service가
     * 멀쩡히 답했다는 뜻이다 (ADR-0008).
     *
     * <p>전송 실패와 5xx는 503으로 옮긴다. 둘 다 세션에 대해 아무것도 알아내지 못한 상태이며
     * 우리 쪽 문제다. 하위 상태를 그대로 흘리면 member-service의 500이 게이트웨이의 500으로
     * 읽혀 재시도 판단이 반대가 된다 — BL-0043이 신원 확인 경로에서 고친 것과 같은 문제다.
     * 브레이커가 둘을 같게 취급하므로 클라이언트에게도 같게 나가야 한다.
     */
    public AccessTokenResponse refresh(String sessionToken) {
        try {
            var issued = members.post()
                    .uri("/internal/members/sessions/refresh")
                    .body(new SessionTokenRequest(sessionToken))
                    .retrieve()
                    .body(AccessTokenResponse.class);
            memberServiceAvailability.recordReachable();
            return issued;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                // 쓸 수 없는 세션이다. member-service는 멀쩡히 답했으므로 장애가 아니다.
                memberServiceAvailability.recordReachable();
                throw new DomainException("unauthorized", "authentication is required", 401);
            }
            if (exception.getStatusCode().is5xxServerError()) {
                memberServiceAvailability.recordUnreachable();
                throw DomainException.unavailable("session could not be refreshed");
            }
            throw exception;
        } catch (ResourceAccessException exception) {
            memberServiceAvailability.recordUnreachable();
            throw DomainException.unavailable("session could not be refreshed");
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

    public MemberResponse updateAddress(String memberId, String addressId, UpdateAddressRequest request) {
        return members.put()
                .uri("/members/me/addresses/{addressId}", addressId)
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .body(MemberResponse.class);
    }

    public MemberResponse removeAddress(String memberId, String addressId, long expectedVersion) {
        return members.delete()
                .uri(builder -> builder.path("/members/me/addresses/{addressId}")
                        .queryParam("expectedVersion", expectedVersion).build(addressId))
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(MemberResponse.class);
    }

    public MemberResponse setDefaultAddress(String memberId, String addressId, SetDefaultAddressRequest request) {
        return members.put()
                .uri("/members/me/addresses/{addressId}/default", addressId)
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .body(MemberResponse.class);
    }

    public StorefrontCartResponse cart(String memberId) {
        return cartPage.get()
                .uri("/cart")
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(StorefrontCartResponse.class);
    }

    public CartResponse addCartItem(String memberId, CartItemRequest request) {
        return storefront.post()
                .uri("/cart/items")
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .body(CartResponse.class);
    }

    public CartResponse changeCartQuantity(String memberId, String skuId, ChangeCartQuantityRequest request) {
        return storefront.put()
                .uri("/cart/items/{skuId}", skuId)
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .body(CartResponse.class);
    }

    public CartResponse removeCartItem(String memberId, String skuId, long expectedVersion) {
        return storefront.delete()
                .uri(builder -> builder.path("/cart/items/{skuId}")
                        .queryParam("expectedVersion", expectedVersion)
                        .build(skuId))
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(CartResponse.class);
    }

    public ResponseEntity<CheckoutResponse> checkout(String memberId, String idempotencyKey, ConfirmedCheckoutRequest request) {
        return storefront.post()
                .uri("/checkout")
                .header(MEMBER_ID_HEADER, memberId)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .toEntity(CheckoutResponse.class);
    }

    public OrderDetailResponse order(String memberId, String orderId) {
        return orders.get()
                .uri("/orders/{orderId}", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(OrderDetailResponse.class);
    }

    public ResponseEntity<OrderCancellationResponse> cancelOrder(String memberId, String orderId) {
        return orders.post()
                .uri("/orders/{orderId}/cancellation", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .toEntity(OrderCancellationResponse.class);
    }

    public ResponseEntity<OrderReturnResponse> requestReturn(
            String memberId, String orderId, RequestOrderReturn request
    ) {
        return orders.post()
                .uri("/orders/{orderId}/returns", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .toEntity(OrderReturnResponse.class);
    }

    public OrderReturnResponse orderReturn(String memberId, String orderId) {
        return orders.get()
                .uri("/orders/{orderId}/return", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(OrderReturnResponse.class);
    }

    public ResponseEntity<OrderReturnResponse> withdrawReturn(String memberId, String orderId) {
        return orders.post()
                .uri("/orders/{orderId}/return/withdrawal", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .toEntity(OrderReturnResponse.class);
    }

    public ResponseEntity<OrderReturnResponse> rescheduleReturn(
            String memberId, String orderId, RescheduleReturnPickupRequest request
    ) {
        return orders.post()
                .uri("/orders/{orderId}/return/reschedule", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .body(request)
                .retrieve()
                .toEntity(OrderReturnResponse.class);
    }

    public OrderPageResponse orders(String memberId, String cursor, int size) {
        return orders.get()
                .uri(builder -> builder.path("/orders")
                        .queryParamIfPresent("cursor", Optional.ofNullable(cursor))
                        .queryParam("size", size).build())
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve().body(OrderPageResponse.class);
    }

    public CheckoutResponse checkoutResult(String memberId, String orderId) {
        return orders.get()
                .uri("/orders/{orderId}/checkout-result", orderId)
                .header(MEMBER_ID_HEADER, memberId)
                .retrieve()
                .body(CheckoutResponse.class);
    }

    public CarrierEventResponse carrierEvent(CarrierEventRequest event) {
        return shipping.post().uri("/internal/carrier-events").body(event).retrieve()
                .body(CarrierEventResponse.class);
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
