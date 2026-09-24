package com.impati.commerce.shipping.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.time.OffsetDateTime;

public final class ShippingModels {
    private ShippingModels() {
    }

    public enum ShipmentStatus {
        READY,
        AWAITING_PICKUP,
        IN_TRANSIT,
        DELIVERED,
        RETURNING,
        RETURNED,
        CANCELLED
    }

    public enum RegistrationStatus { NOT_REQUESTED, PENDING, CONFIRMED, CANCELLED }

    public enum CarrierEventType { PICKED_UP, IN_TRANSIT, DELIVERED, DELIVERY_FAILED, RETURNED }

    public enum EventDecision { APPLIED, IGNORED_STALE, CONFLICT }

    /** 배송 요청 시점의 배송지 스냅샷. */
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
        private ShipmentStatus status;
        private RegistrationStatus registrationStatus;
        private String carrierCode;
        private String carrierName;
        private String trackingNumber;
        private OffsetDateTime lastCarrierEventAt;

        public Shipment(String orderId, String memberId, Address address) {
            this(Ids.newId("shp"), orderId, memberId, address, ShipmentStatus.READY,
                    RegistrationStatus.NOT_REQUESTED, null, null, null, null);
        }

        private Shipment(
                String id,
                String orderId,
                String memberId,
                Address address,
                ShipmentStatus status,
                RegistrationStatus registrationStatus,
                String carrierCode,
                String carrierName,
                String trackingNumber,
                OffsetDateTime lastCarrierEventAt
        ) {
            this.id = id;
            this.orderId = orderId;
            this.memberId = memberId;
            this.address = address;
            this.status = status;
            this.registrationStatus = registrationStatus;
            this.carrierCode = carrierCode;
            this.carrierName = carrierName;
            this.trackingNumber = trackingNumber;
            this.lastCarrierEventAt = lastCarrierEventAt;
        }

        public static Shipment restore(
                String id,
                String orderId,
                String memberId,
                Address address,
                String status,
                String registrationStatus,
                String carrierCode,
                String carrierName,
                String trackingNumber,
                OffsetDateTime lastCarrierEventAt
        ) {
            return new Shipment(id, orderId, memberId, address, ShipmentStatus.valueOf(status),
                    RegistrationStatus.valueOf(registrationStatus), carrierCode, carrierName, trackingNumber,
                    lastCarrierEventAt);
        }

        public String id() { return id; }
        public String orderId() { return orderId; }
        public String memberId() { return memberId; }
        public Address address() { return address; }
        public ShipmentStatus status() { return status; }
        public RegistrationStatus registrationStatus() { return registrationStatus; }
        public String carrierCode() { return carrierCode; }
        public String carrierName() { return carrierName; }
        public String trackingNumber() { return trackingNumber; }
        public OffsetDateTime lastCarrierEventAt() { return lastCarrierEventAt; }

        public boolean requestRegistration() {
            if (registrationStatus == RegistrationStatus.CONFIRMED) return false;
            if (status != ShipmentStatus.READY || registrationStatus == RegistrationStatus.CANCELLED) {
                throw DomainException.conflict("shipment cannot be registered with a carrier");
            }
            registrationStatus = RegistrationStatus.PENDING;
            return true;
        }

        public void confirmRegistration(String code, String name, String tracking) {
            requireText(code, "carrier code");
            requireText(name, "carrier name");
            requireText(tracking, "tracking number");
            if (registrationStatus == RegistrationStatus.CONFIRMED) {
                if (!code.equals(carrierCode) || !name.equals(carrierName) || !tracking.equals(trackingNumber)) {
                    throw DomainException.conflict("carrier registration changed its confirmed result");
                }
                return;
            }
            if (status != ShipmentStatus.READY || registrationStatus != RegistrationStatus.PENDING) {
                throw DomainException.conflict("shipment registration is not pending");
            }
            carrierCode = code;
            carrierName = name;
            trackingNumber = tracking;
            registrationStatus = RegistrationStatus.CONFIRMED;
            status = ShipmentStatus.AWAITING_PICKUP;
        }

        /** 집하 전 취소만 허용하며 반복 취소는 같은 결과를 유지한다. */
        public boolean cancel() {
            if (status == ShipmentStatus.CANCELLED) return false;
            if (status != ShipmentStatus.READY && status != ShipmentStatus.AWAITING_PICKUP) {
                throw DomainException.conflict("shipment already left and cannot be cancelled");
            }
            status = ShipmentStatus.CANCELLED;
            registrationStatus = RegistrationStatus.CANCELLED;
            return true;
        }

        public EventDecision apply(CarrierEventType eventType, OffsetDateTime occurredAt) {
            if (occurredAt == null) throw DomainException.validation("carrier event time is required");
            if (lastCarrierEventAt != null && occurredAt.isBefore(lastCarrierEventAt)) {
                return EventDecision.IGNORED_STALE;
            }
            if (status == ShipmentStatus.CANCELLED) return EventDecision.CONFLICT;
            if (status == ShipmentStatus.DELIVERED) {
                return eventType == CarrierEventType.DELIVERY_FAILED || eventType == CarrierEventType.RETURNED
                        ? EventDecision.CONFLICT : EventDecision.IGNORED_STALE;
            }
            if (status == ShipmentStatus.RETURNED) {
                return eventType == CarrierEventType.DELIVERED
                        ? EventDecision.CONFLICT : EventDecision.IGNORED_STALE;
            }
            if (status == ShipmentStatus.READY) return EventDecision.CONFLICT;

            var next = switch (status) {
                case AWAITING_PICKUP, IN_TRANSIT -> switch (eventType) {
                    case PICKED_UP, IN_TRANSIT -> ShipmentStatus.IN_TRANSIT;
                    case DELIVERED -> ShipmentStatus.DELIVERED;
                    case DELIVERY_FAILED -> ShipmentStatus.RETURNING;
                    case RETURNED -> ShipmentStatus.RETURNED;
                };
                case RETURNING -> switch (eventType) {
                    case RETURNED -> ShipmentStatus.RETURNED;
                    case DELIVERED -> null;
                    case PICKED_UP, IN_TRANSIT, DELIVERY_FAILED -> status;
                };
                default -> throw new IllegalStateException("unexpected shipment status " + status);
            };
            if (next == null) return EventDecision.CONFLICT;
            if (next == status) return EventDecision.IGNORED_STALE;
            status = next;
            lastCarrierEventAt = occurredAt;
            return EventDecision.APPLIED;
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) throw DomainException.validation(field + " is required");
        }
    }
}
