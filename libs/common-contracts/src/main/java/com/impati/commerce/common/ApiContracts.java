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

    public record CapturePaymentRequest(String orderId, String memberId, Money amount, String paymentToken) {
    }

    public record PaymentResponse(
            String id,
            String orderId,
            String memberId,
            Money amount,
            String method,
            String status,
            String transactionId
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

    public record VerifyEmailRequest(String token) {
    }

    public record LoginRequest(String email, String password) {
    }

    /** 로그인 결과. token은 불투명 문자열이며 서버가 해시만 보관한다. */
    public record LoginResponse(String token, String expiresAt) {
    }

    /** 세션이 가리키는 회원. 게이트웨이가 신원을 확인할 때 쓴다. */
    public record SessionResponse(String memberId) {
    }

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

