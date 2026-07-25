package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.util.ArrayList;
import java.util.List;

public final class OrderModels {
    private OrderModels() {
    }

    /**
     * 주문 시점의 배송지 스냅샷. 회원의 주소록이 바뀌어도 주문에 남은 값은 변하지 않는다.
     *
     * <p>alias와 defaultAddress는 회원 주소록의 개념이고 배송에는 쓰이지 않는다.
     * 응답 형태를 유지하기 위해 함께 스냅샷할 뿐이다.
     */
    public record Address(
            String id,
            String alias,
            String recipient,
            String phone,
            String line1,
            String city,
            String postalCode,
            boolean defaultAddress
    ) {
        public Address {
            if (recipient == null || recipient.isBlank()) {
                throw DomainException.validation("recipient is required");
            }
            if (line1 == null || line1.isBlank()) {
                throw DomainException.validation("address line is required");
            }
        }
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
    }

    public static final class Order {
        private final String id;
        private final String memberId;
        private final List<OrderLine> lines;
        private final Address shippingAddress;
        private String status = "CREATED";
        private String paymentId;
        private String shipmentId;
        private String inventoryReservationId;

        public Order(String memberId, List<OrderLine> lines, Address shippingAddress) {
            this(Ids.newId("ord"), memberId, lines, shippingAddress);
        }

        private Order(String id, String memberId, List<OrderLine> lines, Address shippingAddress) {
            if (lines.isEmpty()) {
                throw DomainException.validation("order requires at least one line");
            }
            this.id = id;
            this.memberId = memberId;
            this.lines = new ArrayList<>(lines);
            this.shippingAddress = shippingAddress;
        }

        /**
         * 저장된 상태에서 주문을 복원한다.
         *
         * <p>상태 전이 규칙(markPaid 등)을 거치지 않고 status를 그대로 세운다. CANCELLED처럼
         * 전이를 재생해서는 도달할 수 없는 상태가 있기 때문이다. 영속화 어댑터만 쓴다.
         */
        public static Order restore(
                String id,
                String memberId,
                List<OrderLine> lines,
                Address shippingAddress,
                String status,
                String paymentId,
                String shipmentId,
                String inventoryReservationId
        ) {
            var order = new Order(id, memberId, lines, shippingAddress);
            order.status = status;
            order.paymentId = paymentId;
            order.shipmentId = shipmentId;
            order.inventoryReservationId = inventoryReservationId;
            return order;
        }

        public String id() {
            return id;
        }

        public String memberId() {
            return memberId;
        }

        public String status() {
            return status;
        }

        public List<OrderLine> lines() {
            return List.copyOf(lines);
        }

        public Address shippingAddress() {
            return shippingAddress;
        }

        public String paymentId() {
            return paymentId;
        }

        public String shipmentId() {
            return shipmentId;
        }

        public String inventoryReservationId() {
            return inventoryReservationId;
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
    }
}
