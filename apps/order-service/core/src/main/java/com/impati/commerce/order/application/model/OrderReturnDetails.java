package com.impati.commerce.order.application.model;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.domain.OrderModels.Address;
import java.time.OffsetDateTime;

public record OrderReturnDetails(String id, String orderId, String reason, String description, Money refundAmount,
        String status, String refundStatus, String inventoryStatus, String returnShipmentId, Address pickupAddress,
        OffsetDateTime createdAt, OffsetDateTime receivedAt, OffsetDateTime inspectionDueAt) { }
