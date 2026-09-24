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

    public enum RegistrationStatus {NOT_REQUESTED, PENDING, CONFIRMED, CANCELLED}

    public enum CancellationStatus {NOT_REQUESTED, PENDING, CONFIRMED, ATTENTION_REQUIRED}

    public enum CarrierEventType {PICKED_UP, IN_TRANSIT, DELIVERED, DELIVERY_FAILED, RETURNED}

    /**
     * 택배사 사건을 현재 배송에 적용한 결과.
     *
     * <p>{@code APPLIED}만 고객용 상태 사건을 만들고, {@code NO_TRANSITION}은 원본 수신 이력만
     * 남긴다. {@code IGNORED_STALE}은 이미 반영한 상태 전이보다 과거인 사건이며,
     * {@code CONFLICT}는 취소나 종결 상태와 양립할 수 없어 운영 확인이 필요한 사건이다.
     */
    public enum EventDecision {APPLIED, NO_TRANSITION, IGNORED_STALE, CONFLICT}

    /**
     * 배송 요청 시점의 배송지 스냅샷.
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
        private ShipmentStatus status;
        private RegistrationStatus registrationStatus;
        private CancellationStatus cancellationStatus;
        private String carrierCode;
        private String carrierName;
        private String trackingNumber;
        private OffsetDateTime lastCarrierEventAt;

        public Shipment(String orderId, String memberId, Address address) {
            this(Ids.newId("shp"), orderId, memberId, address, ShipmentStatus.READY,
                    RegistrationStatus.NOT_REQUESTED, CancellationStatus.NOT_REQUESTED,
                    null, null, null, null);
        }

        private Shipment(
                String id,
                String orderId,
                String memberId,
                Address address,
                ShipmentStatus status,
                RegistrationStatus registrationStatus,
                CancellationStatus cancellationStatus,
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
            this.cancellationStatus = cancellationStatus;
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
                String cancellationStatus,
                String carrierCode,
                String carrierName,
                String trackingNumber,
                OffsetDateTime lastCarrierEventAt
        ) {
            return new Shipment(id, orderId, memberId, address, ShipmentStatus.valueOf(status),
                    RegistrationStatus.valueOf(registrationStatus), CancellationStatus.valueOf(cancellationStatus),
                    carrierCode, carrierName, trackingNumber, lastCarrierEventAt);
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

        public ShipmentStatus status() {
            return status;
        }

        public RegistrationStatus registrationStatus() {
            return registrationStatus;
        }

        public CancellationStatus cancellationStatus() {
            return cancellationStatus;
        }

        public String carrierCode() {
            return carrierCode;
        }

        public String carrierName() {
            return carrierName;
        }

        public String trackingNumber() {
            return trackingNumber;
        }

        public OffsetDateTime lastCarrierEventAt() {
            return lastCarrierEventAt;
        }

        public boolean requestRegistration() {
            if (registrationStatus == RegistrationStatus.CONFIRMED) {
                return false;
            }
            if (registrationStatus == RegistrationStatus.PENDING) {
                return false;
            }
            if (status != ShipmentStatus.READY || registrationStatus == RegistrationStatus.CANCELLED
                    || cancellationStatus != CancellationStatus.NOT_REQUESTED) {
                throw DomainException.conflict("shipment cannot be registered with a carrier");
            }
            registrationStatus = RegistrationStatus.PENDING;
            return true;
        }

        public boolean confirmRegistration(String code, String name, String tracking) {
            requireText(code, "carrier code");
            requireText(name, "carrier name");
            requireText(tracking, "tracking number");
            if (registrationStatus == RegistrationStatus.CONFIRMED) {
                if (!code.equals(carrierCode) || !name.equals(carrierName) || !tracking.equals(trackingNumber)) {
                    throw DomainException.conflict("carrier registration changed its confirmed result");
                }
                return false;
            }
            if (status != ShipmentStatus.READY || registrationStatus != RegistrationStatus.PENDING) {
                throw DomainException.conflict("shipment registration is not pending");
            }
            carrierCode = code;
            carrierName = name;
            trackingNumber = tracking;
            registrationStatus = RegistrationStatus.CONFIRMED;
            status = ShipmentStatus.AWAITING_PICKUP;
            return true;
        }

        /** 집하 전 배송에 대한 택배 취소 작업을 시작한다. 반복 요청은 기존 작업을 유지한다. */
        public boolean requestCancellation() {
            if (status == ShipmentStatus.CANCELLED || cancellationStatus == CancellationStatus.PENDING
                    || cancellationStatus == CancellationStatus.CONFIRMED) {
                return false;
            }
            if (status != ShipmentStatus.READY && status != ShipmentStatus.AWAITING_PICKUP) {
                throw DomainException.conflict("shipment already left and cannot be cancelled");
            }
            if (cancellationStatus == CancellationStatus.ATTENTION_REQUIRED) {
                throw DomainException.downstreamError("shipment cancellation requires operational attention");
            }
            cancellationStatus = CancellationStatus.PENDING;
            return true;
        }

        /** 택배사 취소 또는 접수 부재가 확인된 뒤에만 배송 취소를 확정한다. */
        public boolean confirmCancellation() {
            if (status == ShipmentStatus.CANCELLED) {
                return false;
            }
            if (status != ShipmentStatus.READY && status != ShipmentStatus.AWAITING_PICKUP) {
                throw DomainException.conflict("shipment already left and cannot be cancelled");
            }
            status = ShipmentStatus.CANCELLED;
            registrationStatus = RegistrationStatus.CANCELLED;
            cancellationStatus = CancellationStatus.CONFIRMED;
            return true;
        }

        public void requireCancellationAttention() {
            if (status != ShipmentStatus.CANCELLED) {
                cancellationStatus = CancellationStatus.ATTENTION_REQUIRED;
            }
        }

        /**
         * 인증·정규화된 택배사 사실을 현재 배송 상태에 반영한다.
         *
         * <p>취소된 배송에 집하·배송 사실이 오면 둘을 동시에 참으로 만들 수 없으므로
         * {@link EventDecision#CONFLICT}를 반환해 자동 전이 대신 운영 확인으로 보낸다.
         */
        public EventDecision applyCarrierEvent(CarrierEventType eventType, OffsetDateTime occurredAt) {
            if (occurredAt == null) {
                throw DomainException.validation("carrier event time is required");
            }
            if (lastCarrierEventAt != null && occurredAt.isBefore(lastCarrierEventAt)) {
                return EventDecision.IGNORED_STALE;
            }
            if (status == ShipmentStatus.CANCELLED) {
                return EventDecision.CONFLICT;
            }
            if (status == ShipmentStatus.DELIVERED) {
                return eventType == CarrierEventType.DELIVERY_FAILED || eventType == CarrierEventType.RETURNED
                        ? EventDecision.CONFLICT : EventDecision.IGNORED_STALE;
            }
            if (status == ShipmentStatus.RETURNED) {
                return eventType == CarrierEventType.DELIVERED
                        ? EventDecision.CONFLICT : EventDecision.IGNORED_STALE;
            }
            if (status == ShipmentStatus.READY) {
                return EventDecision.CONFLICT;
            }

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
            if (next == null) {
                return EventDecision.CONFLICT;
            }
            if (next == status) {
                return EventDecision.NO_TRANSITION;
            }
            status = next;
            lastCarrierEventAt = occurredAt;
            return EventDecision.APPLIED;
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw DomainException.validation(field + " is required");
            }
        }
    }
}
