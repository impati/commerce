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
import com.impati.commerce.order.adapter.out.client.CommerceClients;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final CommerceClients clients;

    public OrderService(OrderRepository orders, CommerceClients clients) {
        this.orders = orders;
        this.clients = clients;
    }

    public CheckoutResponse checkout(String memberId, String paymentToken, String addressId) {
        var member = clients.member(memberId);
        var address = OrderMapper.toAddress(selectAddress(member.addresses(), addressId));
        var cart = clients.cart(memberId);
        if (cart.lines().isEmpty()) {
            throw DomainException.validation("cart is empty");
        }

        var orderLines = cart.lines().stream().map(line -> {
            var sku = clients.sku(line.skuId());
            var product = clients.product(sku.productId());
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
            reservation = clients.reserve(new ReserveInventoryRequest(
                    order.id(),
                    cart.lines().stream()
                            .map(line -> new ReservationLine(line.skuId(), line.quantity()))
                            .toList()
            ));
            order.attachReservation(reservation.id());
            orders.save(order);

            var payment = clients.capturePayment(new CapturePaymentRequest(
                    order.id(),
                    memberId,
                    order.total(),
                    paymentToken
            ));
            clients.commitReservation(reservation.id());
            reservationCommitted = true;
            order.markPaid(payment.id());
            orders.save(order);

            var shipment = clients.createShipment(new CreateShipmentRequest(
                    order.id(),
                    memberId,
                    OrderMapper.toResponse(address)
            ));
            order.attachShipment(shipment.id());
            orders.save(order);
            clients.clearCart(memberId);

            clients.notify(new NotificationEventRequest(
                    "OrderPaid",
                    memberId,
                    "Order paid",
                    "Order " + order.id() + " has been paid."
            ));
            clients.notify(new NotificationEventRequest(
                    "ShipmentCreated",
                    memberId,
                    "Shipment ready",
                    "Tracking number: " + shipment.trackingNumber()
            ));
            return new CheckoutResponse(OrderMapper.toResponse(order), payment, shipment);
        } catch (RuntimeException exception) {
            if (reservation != null && reservation.status().equals("RESERVED") && !reservationCommitted) {
                clients.releaseReservation(reservation.id());
            }
            order.cancel();
            orders.save(order);
            clients.notify(new NotificationEventRequest(
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
        clients.notify(new NotificationEventRequest(
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
