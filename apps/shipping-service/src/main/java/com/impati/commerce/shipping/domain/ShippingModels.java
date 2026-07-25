package com.impati.commerce.shipping.domain;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

public final class ShippingModels {
    private ShippingModels() {
    }

    public static final class Shipment {
        private final String id;
        private final String orderId;
        private final String memberId;
        private final AddressResponse address;
        private final String trackingNumber;
        private String status = "READY";

        public Shipment(String orderId, String memberId, AddressResponse address) {
            this.id = Ids.newId("shp");
            this.orderId = orderId;
            this.memberId = memberId;
            this.address = address;
            this.trackingNumber = Ids.newId("trk");
        }

        public String id() {
            return id;
        }

        public String orderId() {
            return orderId;
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

        public ShipmentResponse toResponse() {
            return new ShipmentResponse(id, orderId, memberId, address, status, trackingNumber);
        }
    }
}

