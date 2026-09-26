package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.model.OrderReturnDetails;
import com.impati.commerce.order.application.port.in.OrderReturnUseCase;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.ReturnProgressRepository;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.application.port.out.TransactionSection;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.OrderStatus;
import com.impati.commerce.order.domain.ReturnProgress;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrderReturnExecutor implements OrderReturnUseCase {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> CUSTOMER_REASONS = Set.of("CHANGE_OF_MIND", "DEFECT_DAMAGE", "WRONG_ITEM");
    private final OrderRepository orders;
    private final ReturnProgressRepository returns;
    private final ShippingClient shipping;
    private final OrderChanges orderChanges;
    private final TransactionSection transactions;
    private final Clock clock;

    public OrderReturnExecutor(OrderRepository orders, ReturnProgressRepository returns, ShippingClient shipping,
            OrderChanges orderChanges, TransactionSection transactions, Clock clock) {
        this.orders = orders; this.returns = returns; this.shipping = shipping;
        this.orderChanges = orderChanges; this.transactions = transactions; this.clock = clock;
    }

    @Override
    public OrderReturnDetails request(String memberId, String orderId, String reason, String description,
            LocalDate awareDate, Address pickupAddress) {
        var existing = returns.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().status() != ReturnProgress.Status.WITHDRAWN) {
            return owned(existing.get(), memberId);
        }
        var order = requireOrder(memberId, orderId);
        if (order.status() != OrderStatus.DELIVERED) {
            throw DomainException.conflict("only a delivered order can be returned");
        }
        validateReason(reason, description);
        var deliveredAt = orders.deliveredAt(orderId)
                .orElseThrow(() -> DomainException.conflict("order delivery time is missing"));
        validateWindow(reason, awareDate, deliveredAt);
        var address = pickupAddress == null ? order.shippingAddress() : pickupAddress;
        var refundAmount = refundAmount(order.total(), order.priceBreakdown().shippingFee(), reason);
        var progress = new ReturnProgress(orderId, memberId, reason, description, awareDate, refundAmount,
                address, now());
        transactions.run(() -> {
            if (!returns.insertIfAbsent(progress)) {
                throw DomainException.conflict("order already has an active return");
            }
            order.recordReturnAccepted(progress.id(), reason);
            orderChanges.commit(order);
        });
        schedule(progress.id());
        return details(require(progress.id()));
    }

    @Override public OrderReturnDetails getOwned(String memberId, String orderId) {
        return owned(returns.findByOrderId(orderId)
                .orElseThrow(() -> DomainException.notFound("return not found")), memberId);
    }

    @Override public OrderReturnDetails withdraw(String memberId, String orderId) {
        var progress = ownedProgress(memberId, orderId);
        transactions.run(() -> { var locked = requireForUpdate(progress.id()); locked.requestWithdrawal(); returns.save(locked); });
        withdraw(progress.id());
        return details(require(progress.id()));
    }

    @Override public OrderReturnDetails reschedule(String memberId, String orderId, Address pickupAddress) {
        if (pickupAddress == null) throw DomainException.validation("pickup address is required");
        var progress = ownedProgress(memberId, orderId);
        transactions.run(() -> { var locked = requireForUpdate(progress.id()); locked.requestReschedule(pickupAddress); returns.save(locked); });
        reschedule(progress.id());
        return details(require(progress.id()));
    }

    @Override public OrderReturnDetails inspect(String returnId, String disposition, String condition) {
        transactions.run(() -> { var progress = requireForUpdate(returnId); progress.inspect(disposition, condition); returns.save(progress); });
        return details(require(returnId));
    }

    void schedule(String returnId) {
        var progress = require(returnId);
        try {
            var shipment = shipping.createReturnShipment(progress.id(), progress.orderId(), progress.memberId(),
                    address(progress.pickupAddress()));
            transactions.run(() -> { var locked = requireForUpdate(returnId); locked.pickupScheduled(shipment.id()); returns.save(locked); });
        } catch (RuntimeException ignored) { /* durable REQUESTED intent is retried by worker */ }
    }
    void withdraw(String returnId) {
        var progress = require(returnId);
        if (progress.returnShipmentId() == null) {
            transactions.run(() -> { var locked = requireForUpdate(returnId); locked.withdrawn(); returns.save(locked); });
            return;
        }
        try {
            var shipment = shipping.withdrawReturn(progress.returnShipmentId());
            if ("CANCELLED".equals(shipment.status())) transactions.run(() -> {
                var locked = requireForUpdate(returnId); locked.withdrawn(); returns.save(locked);
            });
        } catch (RuntimeException ignored) { }
    }
    void reschedule(String returnId) {
        var progress = require(returnId);
        try {
            var shipment = shipping.rescheduleReturn(progress.returnShipmentId(), address(progress.pickupAddress()));
            transactions.run(() -> { var locked = requireForUpdate(returnId); locked.pickupScheduled(shipment.id()); returns.save(locked); });
        } catch (RuntimeException ignored) { }
    }

    private void validateWindow(String reason, LocalDate awareDate, OffsetDateTime deliveredAt) {
        var today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        var deliveryDate = deliveredAt.atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
        if ("CHANGE_OF_MIND".equals(reason)) {
            if (today.isAfter(deliveryDate.plusDays(7))) throw DomainException.conflict("return request period has expired");
            return;
        }
        if (awareDate == null || awareDate.isAfter(today) || today.isAfter(deliveryDate.plusMonths(3))
                || today.isAfter(awareDate.plusDays(30))) {
            throw DomainException.conflict("defect return request period has expired");
        }
    }
    private static void validateReason(String reason, String description) {
        if (!CUSTOMER_REASONS.contains(reason)) throw DomainException.validation("invalid return reason");
        if (description != null && description.length() > 500) throw DomainException.validation("return description is too long");
    }
    private static Money refundAmount(Money total, Money shippingFee, String reason) {
        return "CHANGE_OF_MIND".equals(reason) && shippingFee.amount() > 0
                ? new Money(total.amount() - 3_000, total.currency()) : total;
    }
    private com.impati.commerce.order.domain.OrderModels.Order requireOrder(String memberId, String orderId) {
        return orders.findByIdAndMemberId(orderId, memberId).orElseThrow(() -> DomainException.notFound("order not found"));
    }
    private ReturnProgress ownedProgress(String memberId, String orderId) {
        var progress = returns.findByOrderId(orderId).orElseThrow(() -> DomainException.notFound("return not found"));
        owned(progress, memberId); return progress;
    }
    private OrderReturnDetails owned(ReturnProgress progress, String memberId) {
        if (!progress.memberId().equals(memberId)) throw DomainException.notFound("return not found");
        return details(progress);
    }
    private ReturnProgress require(String id) { return returns.findById(id).orElseThrow(() -> DomainException.notFound("return not found")); }
    private ReturnProgress requireForUpdate(String id) { return returns.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("return not found")); }
    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    static AddressResponse address(Address a) { return new AddressResponse(a.id(), a.alias(), a.recipient(), a.phone(), a.line1(), a.city(), a.postalCode(), a.defaultAddress()); }
    static OrderReturnDetails details(ReturnProgress p) { return new OrderReturnDetails(p.id(), p.orderId(), p.reason(), p.description(), p.refundAmount(), p.status().name(), p.refundStatus().name(), p.inventoryStatus().name(), p.returnShipmentId(), p.pickupAddress(), p.createdAt(), p.receivedAt(), p.inspectionDueAt()); }
}
