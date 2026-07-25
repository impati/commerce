package com.impati.commerce.shipping.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

public final class ShippingModels {
    private ShippingModels() {
    }

    /**
     * 배송 요청 시점의 배송지 스냅샷. 회원의 주소록이 바뀌어도 배송에 남은 값은 변하지 않는다.
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

    public static final class Shipment {
        private final String id;
        private final String orderId;
        private final String memberId;
        private final Address address;
        private final String trackingNumber;
        private String status;

        public Shipment(String orderId, String memberId, Address address) {
            this(Ids.newId("shp"), orderId, memberId, address, Ids.newId("trk"), "READY");
        }

        private Shipment(
                String id,
                String orderId,
                String memberId,
                Address address,
                String trackingNumber,
                String status
        ) {
            this.id = id;
            this.orderId = orderId;
            this.memberId = memberId;
            this.address = address;
            this.trackingNumber = trackingNumber;
            this.status = status;
        }

        /**
         * 저장된 상태에서 복원한다.
         *
         * <p>상태 전이 규칙을 거치지 않고 status를 그대로 세운다. 영속화 어댑터만 쓴다.
         */
        public static Shipment restore(
                String id,
                String orderId,
                String memberId,
                Address address,
                String trackingNumber,
                String status
        ) {
            return new Shipment(id, orderId, memberId, address, trackingNumber, status);
        }

        public String id() {
            return id;
        }

        public String orderId() {
            return orderId;
        }

        public String memberId() {
            return memberId;
        }

        public Address address() {
            return address;
        }

        public String trackingNumber() {
            return trackingNumber;
        }

        public String status() {
            return status;
        }

        public void ship() {
            if (!status.equals("READY")) {
                throw DomainException.conflict("shipment is not ready");
            }
            status = "IN_TRANSIT";
        }

        public void deliver() {
            if (!status.equals("READY") && !status.equals("IN_TRANSIT")) {
                throw DomainException.conflict("shipment cannot be delivered");
            }
            status = "DELIVERED";
        }
    }
}
