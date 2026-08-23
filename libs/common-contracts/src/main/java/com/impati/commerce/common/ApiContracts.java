package com.impati.commerce.common;

import java.util.List;
import java.util.Map;

public final class ApiContracts {
    private ApiContracts() {
    }

    public record Money(long amount, String currency) {
        public static Money krw(long amount) {
            return new Money(amount, "KRW");
        }
    }

    public record ErrorResponse(String code, String message) {
    }

    public record AddressResponse(
            String id,
            String alias,
            String recipient,
            String phone,
            String line1,
            String city,
            String postalCode,
            boolean defaultAddress
    ) {
    }

    /** POST /members */
    public record RegisterMemberRequest(String email, String name, String password) {
    }

    public record AddAddressRequest(
            String alias,
            String recipient,
            String phone,
            String line1,
            String city,
            String postalCode,
            boolean defaultAddress
    ) {
    }

    public record MemberResponse(
            String id,
            String email,
            String name,
            String status,
            List<AddressResponse> addresses
    ) {
    }

    public record SkuResponse(
            String id,
            String productId,
            String name,
            Money price,
            Map<String, String> attributes,
            String status
    ) {
    }

    public record ProductResponse(
            String id,
            String name,
            String brand,
            String category,
            String description,
            String status,
            List<String> tags,
            List<SkuResponse> skus
    ) {
    }

    public record ProductCard(
            String id,
            String name,
            String brand,
            String category,
            Money price,
            List<String> tags
    ) {
    }

    public record DisplaySection(String key, String title, List<ProductCard> products) {
    }

    public record DisplayHomeResponse(String title, String subtitle, List<DisplaySection> sections) {
    }

    public record StockIncreaseRequest(String skuId, int quantity) {
    }

    public record StockResponse(String skuId, int onHand, int reserved, int available) {
    }

    public record ReservationLine(String skuId, int quantity) {
    }

    public record ReserveInventoryRequest(String orderId, List<ReservationLine> lines) {
    }

    public record ReservationResponse(String id, String orderId, String status, List<ReservationLine> lines) {
    }

    public record CartItemRequest(String skuId, int quantity) {
    }

    public record CartLineResponse(String skuId, int quantity) {
    }

    public record CartResponse(String memberId, List<CartLineResponse> lines) {
    }

    /** order-service의 checkout saga → payment-service의 승인. 매입과 취소는 결제 식별자만 쓴다. */
    public record AuthorizePaymentRequest(String orderId, String memberId, Money amount, String paymentToken) {
    }

    /**
     * payment-service → order-service → 게이트웨이 → 브라우저.
     *
     * <p>대행사 거래 식별자는 담지 않는다. 대사에 쓰는 내부 값이라 형제 서비스도 브라우저도
     * 쓸 일이 없다. payment-service 안에서는 유스케이스 결과 타입이 그것을 들고 있다.
     */
    public record PaymentResponse(
            String id,
            String orderId,
            String memberId,
            Money amount,
            String method,
            String status
    ) {
    }

    public record CreateShipmentRequest(String orderId, String memberId, AddressResponse address) {
    }

    public record ShipmentResponse(
            String id,
            String orderId,
            String memberId,
            AddressResponse address,
            String status,
            String trackingNumber
    ) {
    }

    public record OrderLineResponse(
            String skuId,
            String productId,
            String productName,
            String skuName,
            int quantity,
            Money unitPrice,
            Money lineTotal
    ) {
    }

    public record OrderResponse(
            String id,
            String memberId,
            String status,
            List<OrderLineResponse> lines,
            Money total,
            AddressResponse shippingAddress,
            String paymentId,
            String shipmentId,
            String inventoryReservationId
    ) {
    }

    /** 회원 신원은 X-Member-Id 헤더로 전달된다. 본문에 memberId를 두지 않는다. */
    public record CheckoutRequest(String paymentToken, String addressId) {
    }

    public record CheckoutResponse(OrderResponse order, PaymentResponse payment, ShipmentResponse shipment) {
    }

    public record NotificationEventRequest(String eventType, String memberId, String subject, String body) {
    }

    /** POST /members/verifications — 이메일 소유 인증 토큰. 단일 사용이며 짧은 만료를 갖는다. */
    public record VerifyEmailRequest(String token) {
    }

    /**
     * POST /members/internal/sessions/resolve, POST /members/logout — 세션 토큰.
     *
     * <p>확인과 폐기가 같은 값을 다루므로 한 타입으로 둔다.
     *
     * <p>이메일 인증 토큰과 모양이 같지만 개념이 다르다. 발급 경로, 수명, 단일 사용 여부,
     * 폐기 방식이 모두 다르므로 타입을 나눠 잘못된 사용을 컴파일러가 잡게 한다.
     */
    public record SessionTokenRequest(String token) {
    }

    /** POST /login */
    public record LoginRequest(String email, String password) {
    }

    /**
     * POST /login 응답.
     *
     * <p>{@code sessionToken}은 불투명 문자열이며 서버가 해시만 보관한다. 갱신에만 쓰인다.
     * {@code accessToken}은 서명된 단명 토큰이라 게이트웨이가 조회 없이 검증하며, 담긴 값은
     * 누구나 읽을 수 있다 (ADR-0007).
     */
    public record LoginResponse(
            String sessionToken,
            String sessionExpiresAt,
            String accessToken,
            String accessTokenExpiresAt
    ) {
    }

    /** POST /internal/members/sessions/refresh 응답. 세션 토큰을 새 접근 토큰으로 바꾼다. */
    public record AccessTokenResponse(String accessToken, String accessTokenExpiresAt) {
    }

    /** POST /notifications/email-verifications — member-service가 notification-service에 보낸다. */
    public record EmailVerificationMailRequest(String memberId, String email, String token) {
    }

    /** 로컬 데모에서 발송함을 들여다보기 위한 응답. 운영 프로파일에는 조회 경로가 없다. */
    public record OutboxEntryResponse(
            String id,
            String channel,
            String recipient,
            String subject,
            String body,
            String deliveryStatus,
            int attempts
    ) {
    }

    public record NotificationResponse(
            String id,
            String eventType,
            String memberId,
            String subject,
            String body
    ) {
    }
}

