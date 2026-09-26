package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.RefundPaymentResponse;
import com.impati.commerce.common.ApiContracts.ReturnInventoryResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.in.ReturnRecoveryUseCase;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ReturnProgressRepository;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.application.port.out.TransactionSection;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.ReturnProgress;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReturnRecoveryExecutor implements ReturnRecoveryUseCase {
    private static final Logger log = LoggerFactory.getLogger(ReturnRecoveryExecutor.class);
    private final ReturnProgressRepository returns; private final OrderRepository orders;
    private final ShippingClient shipping; private final PaymentClient payments; private final InventoryClient inventory;
    private final TransactionSection transactions; private final OrderChanges orderChanges; private final Clock clock;

    public ReturnRecoveryExecutor(ReturnProgressRepository returns, OrderRepository orders, ShippingClient shipping,
            PaymentClient payments, InventoryClient inventory, TransactionSection transactions,
            OrderChanges orderChanges, Clock clock) {
        this.returns=returns; this.orders=orders; this.shipping=shipping; this.payments=payments;
        this.inventory=inventory; this.transactions=transactions; this.orderChanges=orderChanges; this.clock=clock;
    }

    @Override public int recover(int batchSize) {
        var items = returns.findRecoverable(now(), batchSize);
        items.forEach(item -> {
            try { recoverOne(item.id()); }
            catch (RuntimeException failure) { log.warn("return recovery will retry id={}", item.id(), failure); }
        });
        return items.size();
    }

    private void recoverOne(String id) {
        var progress = require(id);
        switch (progress.status()) {
            case REQUESTED -> schedule(progress);
            case WITHDRAWAL_PENDING -> withdraw(progress);
            case RESCHEDULE_PENDING -> reschedule(progress);
            default -> { }
        }
        progress = require(id);
        if (progress.status() == ReturnProgress.Status.RECEIVED
                && progress.inventoryStatus() == ReturnProgress.WorkStatus.NOT_READY
                && !progress.inspectionDueAt().isAfter(now())) {
            transactions.run(() -> { var locked=requireForUpdate(id); locked.expireInspection(); returns.save(locked); });
        }
        progress = require(id);
        if (progress.refundStatus() == ReturnProgress.WorkStatus.PENDING) refund(progress);
        progress = require(id);
        if (progress.inventoryStatus() == ReturnProgress.WorkStatus.PENDING) inventory(progress);
        complete(id);
    }

    private void schedule(ReturnProgress p) {
        var known = shipping.returnShipment(p.id());
        var shipment = known.orElseGet(() -> shipping.createReturnShipment(
                p.id(), p.orderId(), p.memberId(), address(p.pickupAddress())));
        transactions.run(() -> { var locked=requireForUpdate(p.id()); locked.pickupScheduled(shipment.id()); returns.save(locked); });
    }
    private void withdraw(ReturnProgress p) {
        var shipment = shipping.returnShipment(p.id()).orElse(null);
        if (shipment == null || "CANCELLED".equals(shipment.status())) {
            transactions.run(() -> { var locked=requireForUpdate(p.id()); locked.withdrawn(); returns.save(locked); });
            return;
        }
        var result = shipping.withdrawReturn(shipment.id());
        if ("CANCELLED".equals(result.status())) transactions.run(() -> {
            var locked=requireForUpdate(p.id()); locked.withdrawn(); returns.save(locked);
        });
    }
    private void reschedule(ReturnProgress p) {
        var shipment = shipping.rescheduleReturn(p.returnShipmentId(), address(p.pickupAddress()));
        transactions.run(() -> { var locked=requireForUpdate(p.id()); locked.pickupScheduled(shipment.id()); returns.save(locked); });
    }
    private void refund(ReturnProgress p) {
        var order = orders.findById(p.orderId()).orElseThrow();
        RefundPaymentResponse result;
        try {
            result = payments.refundReturn(order.paymentId(), p.id(), p.refundAmount());
        } catch (RuntimeException unknown) {
            result = payments.returnRefund(p.id()).orElseThrow(() -> unknown);
        }
        if (!result.paymentId().equals(order.paymentId()) || !result.amount().equals(p.refundAmount())) {
            throw DomainException.conflict("return refund result does not match intent");
        }
        if ("SUCCEEDED".equals(result.status())) transactions.run(() -> {
            var locked=requireForUpdate(p.id()); locked.refundSucceeded(); returns.save(locked);
        });
        else if ("REJECTED".equals(result.status())) attention(p.id());
    }
    private void inventory(ReturnProgress p) {
        var order = orders.findById(p.orderId()).orElseThrow();
        ReturnInventoryResponse result;
        try {
            result = inventory.processReturn(order.inventoryReservationId(), p.id(), p.memberId(),
                    p.disposition(), p.condition());
        } catch (RuntimeException unknown) {
            result = inventory.returnInventory(p.id()).orElseThrow(() -> unknown);
        }
        if (!result.reservationId().equals(order.inventoryReservationId())
                || !result.memberId().equals(p.memberId()) || !result.disposition().equals(p.disposition())
                || !result.condition().equals(p.condition())) {
            throw DomainException.conflict("return inventory result does not match intent");
        }
        transactions.run(() -> { var locked=requireForUpdate(p.id()); locked.inventorySucceeded(); returns.save(locked); });
    }
    private void attention(String id) {
        transactions.run(() -> {
            var progress=requireForUpdate(id); progress.attention(); returns.save(progress);
            var order=orders.findById(progress.orderId()).orElseThrow();
            order.recordReturnAttention(progress.id()); orderChanges.commit(order);
        });
    }
    private void complete(String id) {
        transactions.run(() -> {
            var progress=requireForUpdate(id);
            if (!progress.completeIfReady()) return;
            returns.save(progress);
            var order=orders.findById(progress.orderId()).orElseThrow();
            order.completeReturn(progress.id());
            orderChanges.commit(order);
        });
    }
    private ReturnProgress require(String id) { return returns.findById(id).orElseThrow(); }
    private ReturnProgress requireForUpdate(String id) { return returns.findByIdForUpdate(id).orElseThrow(); }
    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static AddressResponse address(Address a) { return new AddressResponse(a.id(), a.alias(), a.recipient(), a.phone(), a.line1(), a.city(), a.postalCode(), a.defaultAddress()); }
}
