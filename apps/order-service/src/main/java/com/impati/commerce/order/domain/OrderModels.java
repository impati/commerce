package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.OrderLineResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.util.ArrayList;
import java.util.List;

public final class OrderModels {
    private OrderModels() {
    }

    public record OrderLine(
            String skuId,
            String productId,
            String productName,
            String skuName,
            int quantity,
            Money unitPrice
    ) {
        public OrderLine {
            if (quantity <= 0) {
                throw DomainException.validation("order quantity must be positive");
            }
        }

        public Money lineTotal() {
            return new Money(unitPrice.amount() * quantity, unitPrice.currency());
        }

        public OrderLineResponse toResponse() {
            return new OrderLineResponse(
                    skuId,
                    productId,
                    productName,
                    skuName,
                    quantity,
                    unitPrice,
                    lineTotal()
            );
        }
    }

    public static final class Order {
        private final String id;
        private final String memberId;
        private final List<OrderLine> lines;
        private final AddressResponse shippingAddress;
        private String status = "CREATED";
        private String paymentId;
        private String shipmentId;
        private String inventoryReservationId;

        public Order(String memberId, List<OrderLine> lines, AddressResponse shippingAddress) {
            if (lines.isEmpty()) {
                throw DomainException.validation("order requires at least one line");
            }
            this.id = Ids.newId("ord");
            this.memberId = memberId;
            this.lines = new ArrayList<>(lines);
            this.shippingAddress = shippingAddress;
        }

        public String id() {
            return id;
        }

        public String memberId() {
            return memberId;
        }

        public Money total() {
            var amount = lines.stream().mapToLong(line -> line.lineTotal().amount()).sum();
            return new Money(amount, "KRW");
        }

        public void attachReservation(String reservationId) {
            this.inventoryReservationId = reservationId;
        }

        public void markPaid(String paymentId) {
            if (!status.equals("CREATED")) {
                throw DomainException.conflict("order cannot be paid from current status");
            }
            this.paymentId = paymentId;
            this.status = "PAID";
        }

        public void attachShipment(String shipmentId) {
            if (!status.equals("PAID")) {
                throw DomainException.conflict("shipment can only be attached to paid order");
            }
            this.shipmentId = shipmentId;
            this.status = "FULFILLING";
        }

        public void markDelivered() {
            if (!status.equals("FULFILLING") && !status.equals("PAID")) {
                throw DomainException.conflict("order cannot be delivered from current status");
            }
            this.status = "DELIVERED";
        }

        public void cancel() {
            if (status.equals("DELIVERED")) {
                throw DomainException.conflict("delivered order cannot be cancelled");
            }
            this.status = "CANCELLED";
        }

        public OrderResponse toResponse() {
            return new OrderResponse(
                    id,
                    memberId,
                    status,
                    lines.stream().map(OrderLine::toResponse).toList(),
                    total(),
                    shippingAddress,
                    paymentId,
                    shipmentId,
                    inventoryReservationId
            );
        }
    }
}

