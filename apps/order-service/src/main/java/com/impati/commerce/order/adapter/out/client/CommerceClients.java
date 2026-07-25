package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CapturePaymentRequest;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import com.impati.commerce.common.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CommerceClients {
    private final RestClient members;
    private final RestClient carts;
    private final RestClient catalog;
    private final RestClient inventory;
    private final RestClient payments;
    private final RestClient shipping;
    private final RestClient notifications;

    public CommerceClients(
            RestClient.Builder builder,
            @Value("${clients.member.url}") String memberUrl,
            @Value("${clients.cart.url}") String cartUrl,
            @Value("${clients.catalog.url}") String catalogUrl,
            @Value("${clients.inventory.url}") String inventoryUrl,
            @Value("${clients.payment.url}") String paymentUrl,
            @Value("${clients.shipping.url}") String shippingUrl,
            @Value("${clients.notification.url}") String notificationUrl
    ) {
        this.members = builder.clone().baseUrl(memberUrl).build();
        this.carts = builder.clone().baseUrl(cartUrl).build();
        this.catalog = builder.clone().baseUrl(catalogUrl).build();
        this.inventory = builder.clone().baseUrl(inventoryUrl).build();
        this.payments = builder.clone().baseUrl(paymentUrl).build();
        this.shipping = builder.clone().baseUrl(shippingUrl).build();
        this.notifications = builder.clone().baseUrl(notificationUrl).build();
    }

    public MemberResponse member(String memberId) {
        return members.get().uri("/members/{memberId}", memberId).retrieve().body(MemberResponse.class);
    }

    public CartResponse cart(String memberId) {
        return carts.get().uri("/carts/{memberId}", memberId).retrieve().body(CartResponse.class);
    }

    public void clearCart(String memberId) {
        carts.post().uri("/carts/{memberId}/clear", memberId).retrieve().toBodilessEntity();
    }

    public SkuResponse sku(String skuId) {
        return catalog.get().uri("/skus/{skuId}", skuId).retrieve().body(SkuResponse.class);
    }

    public ProductResponse product(String productId) {
        return catalog.get().uri("/products/{productId}", productId).retrieve().body(ProductResponse.class);
    }

    public ReservationResponse reserve(ReserveInventoryRequest request) {
        return inventory.post().uri("/reservations").body(request).retrieve().body(ReservationResponse.class);
    }

    public void commitReservation(String reservationId) {
        inventory.post().uri("/reservations/{reservationId}/commit", reservationId).retrieve().toBodilessEntity();
    }

    public void releaseReservation(String reservationId) {
        inventory.post().uri("/reservations/{reservationId}/release", reservationId).retrieve().toBodilessEntity();
    }

    public PaymentResponse capturePayment(CapturePaymentRequest request) {
        try {
            return payments.post().uri("/payments/capture").body(request).retrieve().body(PaymentResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 402) {
                throw DomainException.paymentDeclined("payment was declined by issuer");
            }
            throw DomainException.conflict("payment service error: " + exception.getStatusText());
        }
    }

    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        return shipping.post().uri("/shipments").body(request).retrieve().body(ShipmentResponse.class);
    }

    public void notify(NotificationEventRequest request) {
        try {
            notifications.post().uri("/notifications/events").body(request).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Notification failure should not roll back checkout in this reference architecture.
        }
    }
}

