package com.impati.commerce.order.application;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CapturePaymentRequest;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * checkout saga를 조율한다.
 *
 * <p>협력자가 일곱인 것은 이 서비스가 saga 조율자이기 때문이다. 각 협력자를 별도 포트로 두어
 * 어떤 서비스에 의존하는지가 생성자에 그대로 드러나게 한다.
 */
@Service
public class OrderService {
    private final OrderRepository orders;
    private final MemberClient members;
    private final CartClient carts;
    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final PaymentClient payments;
    private final ShippingClient shipping;
    private final NotificationClient notifications;

    public OrderService(
            OrderRepository orders,
            MemberClient members,
            CartClient carts,
            CatalogClient catalog,
            InventoryClient inventory,
            PaymentClient payments,
            ShippingClient shipping,
            NotificationClient notifications
    ) {
        this.orders = orders;
        this.members = members;
        this.carts = carts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.payments = payments;
        this.shipping = shipping;
        this.notifications = notifications;
    }

    public CheckoutResponse checkout(String memberId, String paymentToken, String addressId) {
        var member = members.member(memberId);
        var address = OrderMapper.toAddress(selectAddress(member.addresses(), addressId));
        var cart = carts.cart(memberId);
        if (cart.lines().isEmpty()) {
            throw DomainException.validation("cart is empty");
        }

        var orderLines = cart.lines().stream().map(line -> {
            var sku = catalog.sku(line.skuId());
            var product = catalog.product(sku.productId());
            return new OrderLine(
                    sku.id(),
                    product.id(),
                    product.name(),
                    sku.name(),
                    line.quantity(),
                    sku.price()
            );
        }).toList();
        var order = new Order(memberId, orderLines, address);
        orders.save(order);

        ReservationResponse reservation = null;
        boolean reservationCommitted = false;
        try {
            reservation = inventory.reserve(new ReserveInventoryRequest(
                    order.id(),
                    cart.lines().stream()
                            .map(line -> new ReservationLine(line.skuId(), line.quantity()))
                            .toList()
            ));
            order.attachReservation(reservation.id());
            orders.save(order);

            var payment = payments.capturePayment(new CapturePaymentRequest(
                    order.id(),
                    memberId,
                    order.total(),
                    paymentToken
            ));
            inventory.commitReservation(reservation.id());
            reservationCommitted = true;
            order.markPaid(payment.id());
            orders.save(order);

            var shipment = shipping.createShipment(new CreateShipmentRequest(
                    order.id(),
                    memberId,
                    OrderMapper.toResponse(address)
            ));
            order.attachShipment(shipment.id());
            orders.save(order);
            carts.clearCart(memberId);

            notifications.notify(new NotificationEventRequest(
                    "OrderPaid",
                    memberId,
                    "Order paid",
                    "Order " + order.id() + " has been paid."
            ));
            notifications.notify(new NotificationEventRequest(
                    "ShipmentCreated",
                    memberId,
                    "Shipment ready",
                    "Tracking number: " + shipment.trackingNumber()
            ));
            return new CheckoutResponse(OrderMapper.toResponse(order), payment, shipment);
        } catch (RuntimeException exception) {
            if (reservation != null && reservation.status().equals("RESERVED") && !reservationCommitted) {
                inventory.releaseReservation(reservation.id());
            }
            order.cancel();
            orders.save(order);
            notifications.notify(new NotificationEventRequest(
                    "OrderCancelled",
                    memberId,
                    "Order cancelled",
                    "Order " + order.id() + " was cancelled: " + exception.getMessage()
            ));
            throw exception;
        }
    }

    public OrderResponse get(String orderId) {
        return OrderMapper.toResponse(getOrder(orderId));
    }

    public OrderResponse markDelivered(String orderId) {
        var order = getOrder(orderId);
        order.markDelivered();
        orders.save(order);
        notifications.notify(new NotificationEventRequest(
                "OrderDelivered",
                order.memberId(),
                "Order delivered",
                "Order " + order.id() + " has been delivered."
        ));
        return OrderMapper.toResponse(order);
    }

    private AddressResponse selectAddress(List<AddressResponse> addresses, String addressId) {
        if (addresses.isEmpty()) {
            throw DomainException.validation("member has no delivery address");
        }
        if (addressId == null || addressId.isBlank()) {
            return addresses.stream()
                    .filter(AddressResponse::defaultAddress)
                    .findFirst()
                    .orElse(addresses.getFirst());
        }
        return addresses.stream()
                .filter(address -> address.id().equals(addressId))
                .findFirst()
                .orElseThrow(() -> DomainException.validation("address does not belong to member"));
    }

    private Order getOrder(String orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> DomainException.notFound("order not found"));
    }
}
