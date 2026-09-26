package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.OrderReturnResponse;
import com.impati.commerce.order.application.model.OrderReturnDetails;
import com.impati.commerce.order.domain.OrderModels.Address;

final class OrderReturnResponseMapper {
    private OrderReturnResponseMapper() { }
    static OrderReturnResponse from(OrderReturnDetails d) {
        return new OrderReturnResponse(d.id(), d.orderId(), d.reason(), d.description(), d.refundAmount(),
                d.status(), d.refundStatus(), d.inventoryStatus(), d.returnShipmentId(), address(d.pickupAddress()),
                d.createdAt(), d.receivedAt(), d.inspectionDueAt());
    }
    static Address toAddress(AddressResponse a) {
        if (a == null) return null;
        return new Address(a.id(), a.alias(), a.recipient(), a.phone(), a.line1(), a.city(), a.postalCode(), a.defaultAddress());
    }
    private static AddressResponse address(Address a) {
        return new AddressResponse(a.id(), a.alias(), a.recipient(), a.phone(), a.line1(), a.city(), a.postalCode(), a.defaultAddress());
    }
}
