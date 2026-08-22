package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.in.CheckoutResult;
import com.impati.commerce.order.application.port.in.OrderDetails;
import com.impati.commerce.order.application.port.in.OrderUseCase;
import com.impati.commerce.order.application.port.out.CartClient;
import com.impati.commerce.order.application.port.out.CatalogClient;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.MemberClient;
import com.impati.commerce.order.application.port.out.NotificationClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * checkout saga를 조율한다.
 *
 * <p>협력자가 여덟인 것은 이 서비스가 saga 조율자이기 때문이다. 각 협력자를 별도 포트로 두어
 * 어떤 서비스에 의존하는지가 생성자에 그대로 드러나게 한다.
 *
 * <p>순서의 핵심은 <b>매입이 마지막 되돌릴 수 있는 단계보다 뒤에 있다</b>는 것이다
 * (PD-0012-R5). 매입 전의 실패는 배송 취소·승인 취소·예약 해제로 흔적 없이 정리되고,
 * 매입 후에는 되돌리지 않는다 (PD-0012-R6, PD-0012-R8).
 */
@Component
public class OrderExecutor implements OrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(OrderExecutor.class);

    private final OrderRepository orders;
    private final MemberClient members;
    private final CartClient carts;
    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final PaymentClient payments;
    private final ShippingClient shipping;
    private final NotificationClient notifications;

    public OrderExecutor(
            OrderRepository orders,
            MemberClient members,
            CartClient carts,
            CatalogClient catalog,
            InventoryClient inventory,
            PaymentClient payments,
            ShippingClient shipping,
            NotificationClient notifications
    ) {
        this.orders = orders;
        this.members = members;
        this.carts = carts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.payments = payments;
        this.shipping = shipping;
        this.notifications = notifications;
    }

    @Override
    public CheckoutResult checkout(String memberId, String paymentToken, String addressId) {
        var member = members.member(memberId);
        var address = OrderMapper.toAddress(selectAddress(member.addresses(), addressId));
        var cart = carts.cart(memberId);
        if (cart.lines().isEmpty()) {
            throw DomainException.validation("cart is empty");
        }

        var orderLines = cart.lines().stream().map(line -> {
            var sku = catalog.sku(line.skuId());
            var product = catalog.product(sku.productId());
            return new OrderLine(
                    sku.id(),
                    product.id(),
                    product.name(),
                    sku.name(),
                    line.quantity(),
                    sku.price()
            );
        }).toList();
        var order = new Order(memberId, orderLines, address);
        orders.save(order);

        String reservationId = null;
        String paymentId = null;
        ShipmentResponse shipment = null;
        var captureAttempted = new AtomicBoolean(false);
        PaymentResponse payment;
        try {
            reservationId = inventory.reserve(new ReserveInventoryRequest(
                    order.id(),
                    cart.lines().stream()
                            .map(line -> new ReservationLine(line.skuId(), line.quantity()))
                            .toList()
            )).id();
            order.attachReservation(reservationId);
            orders.save(order);

            paymentId = payments.authorizePayment(new AuthorizePaymentRequest(
                    order.id(),
                    memberId,
                    order.total(),
                    paymentToken
            )).id();
            order.attachPayment(paymentId);
            orders.save(order);

            shipment = shipping.createShipment(new CreateShipmentRequest(
                    order.id(),
                    memberId,
                    OrderMapper.toResponse(address)
            ));

            captureAttempted.set(true);
            payment = capture(paymentId);
        } catch (RuntimeException exception) {
            rollbackBeforeCapture(
                    order, reservationId, paymentId, shipment, memberId, captureAttempted.get(), exception);
            throw exception;
        }

        // 매입이 끝났다. 여기부터는 아무것도 되돌리지 않는다 (PD-0012-R8).
        order.markPaid();
        order.attachShipment(shipment.id());
        orders.save(order);

        commitReservationQuietly(order, reservationId);
        clearCartQuietly(order, memberId);
        notifications.notify(new NotificationEventRequest(
                "OrderPaid",
                memberId,
                "Order paid",
                "Order " + order.id() + " has been paid."
        ));
        notifications.notify(new NotificationEventRequest(
                "ShipmentCreated",
                memberId,
                "Shipment ready",
                "Tracking number: " + shipment.trackingNumber()
        ));
        return new CheckoutResult(OrderMapper.toDetails(order), payment, shipment);
    }

    /**
     * 매입한다. 결과를 받지 못하면 한 번 더 시도한다 (PD-0012-R13).
     *
     * <p>응답 유실은 매입이 실패했다는 뜻이 아니다. 요청은 갔고 상대는 처리를 마쳤을 수
     * 있으므로, 다시 부르면 대개 그 결과를 그대로 돌려받는다 — 매입은 멱등하다
     * (PD-0011-R4). 재시도가 곧 확인이므로 별도 조회가 필요 없다.
     *
     * <p>두 번 모두 결과를 받지 못하면 여기서는 확정할 수 없다. 그대로 올려보내고
     * 되돌리는 쪽이 판단한다.
     */
    private PaymentResponse capture(String paymentId) {
        try {
            return payments.capturePayment(paymentId);
        } catch (DomainException exception) {
            if (!exception.code().equals("outcome_unknown")) {
                throw exception;
            }
            log.warn("capture outcome unknown, retrying once payment={}", paymentId);
            return payments.capturePayment(paymentId);
        }
    }

    /**
     * 매입 전 실패를 되돌린다 (PD-0012-R6). 장바구니는 그대로 둔다 (PD-0012-R7).
     *
     * <p>각 단계를 독립적으로 시도한다. 하나가 실패해도 나머지를 시도하며, 원래 실패 원인이
     * 그대로 올라간다 — 되돌리는 도중에 새 예외를 던지면 왜 실패했는지가 사라지고 남은
     * 단계도 실행되지 않는다.
     *
     * <p>예약 해제에 조건이 없는 것은 매입 전에는 예약이 확정된 적이 없기 때문이다. 확정은
     * 매입 뒤에 일어난다 (PD-0012-R5).
     */
    private void rollbackBeforeCapture(
            Order order,
            String reservationId,
            String paymentId,
            ShipmentResponse shipment,
            String memberId,
            boolean captureAttempted,
            RuntimeException cause
    ) {
        if (shipment != null) {
            compensate("cancel-shipment", shipment.id(), () -> shipping.cancelShipment(shipment.id()), cause);
        }
        if (paymentId != null) {
            var id = paymentId;
            compensate("cancel-payment", id, () -> payments.cancelPayment(id), cause);
        }
        if (reservationId != null) {
            var id = reservationId;
            compensate("release-reservation", id, () -> inventory.releaseReservation(id), cause);
        }
        // 매입을 시도했는데 결과를 못 받은 경우에만 표시한다. 그 앞 단계의 결과 불명은
        // 아직 대금이 움직이지 않았으므로 환불 대상이 아니다.
        var captureOutcomeUnknown = captureAttempted
                && cause instanceof DomainException domain
                && domain.code().equals("outcome_unknown");
        var cancelled = compensate("cancel-order", order.id(), () -> {
            order.cancel();
            if (captureOutcomeUnknown) {
                // 매입 여부를 모른 채 취소한다. 환불이 필요한지 나중에 결제에 물어야 한다.
                order.markPaymentOutcomeUnknown();
            }
            orders.save(order);
        }, cause);
        if (!cancelled) {
            // 취소되지 않은 주문에 취소를 알리지 않는다 (PD-0012-R11).
            return;
        }
        notifications.notify(new NotificationEventRequest(
                "OrderCancelled",
                memberId,
                "Order cancelled",
                "Order " + order.id() + " was cancelled: " + cause.getMessage()
        ));
    }

    /** 성공하면 {@code true}. 실패는 원인에 붙이고 삼켜서 남은 보상이 계속 돌게 한다. */
    private boolean compensate(String step, String id, Runnable action, RuntimeException cause) {
        try {
            action.run();
            return true;
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
            log.error("checkout compensation failed step={} id={} cause={}", step, id, cause.getMessage(), failure);
            return false;
        }
    }

    /**
     * 실패해도 되돌리지 않는다 (PD-0012-R9). 예약된 재고는 예약 시점에 이미 가용 수량에서
     * 빠져 있으므로 초과 판매가 생기지 않는다. 남는 것은 보유 수량이 줄지 않은 상태다.
     */
    private void commitReservationQuietly(Order order, String reservationId) {
        try {
            inventory.commitReservation(reservationId);
        } catch (RuntimeException failure) {
            log.error("reservation not committed for paid order order={} reservation={}",
                    order.id(), reservationId, failure);
        }
    }

    /** 실패해도 되돌리지 않는다 (PD-0012-R8). 장바구니에 같은 물건이 남는다. */
    private void clearCartQuietly(Order order, String memberId) {
        try {
            carts.clearCart(memberId);
        } catch (RuntimeException failure) {
            log.error("cart not cleared for paid order order={}", order.id(), failure);
        }
    }

    /**
     * 요청자 소유의 주문만 돌려준다.
     *
     * <p>없는 주문과 남의 주문을 같은 응답으로 거절한다. 구분하면 어떤 주문 id가 존재하는지
     * 알아낼 수 있다.
     */
    @Override
    public OrderDetails getOwned(String memberId, String orderId) {
        var order = getOrder(orderId);
        if (!order.memberId().equals(memberId)) {
            throw DomainException.notFound("order not found");
        }
        return OrderMapper.toDetails(order);
    }

    @Override
    public OrderDetails markDelivered(String orderId) {
        var order = getOrder(orderId);
        order.markDelivered();
        orders.save(order);
        notifications.notify(new NotificationEventRequest(
                "OrderDelivered",
                order.memberId(),
                "Order delivered",
                "Order " + order.id() + " has been delivered."
        ));
        return OrderMapper.toDetails(order);
    }

    private AddressResponse selectAddress(List<AddressResponse> addresses, String addressId) {
        if (addresses.isEmpty()) {
            throw DomainException.validation("member has no delivery address");
        }
        if (addressId == null || addressId.isBlank()) {
            return addresses.stream()
                    .filter(AddressResponse::defaultAddress)
                    .findFirst()
                    .orElse(addresses.getFirst());
        }
        return addresses.stream()
                .filter(address -> address.id().equals(addressId))
                .findFirst()
                .orElseThrow(() -> DomainException.validation("address does not belong to member"));
    }

    private Order getOrder(String orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> DomainException.notFound("order not found"));
    }
}
