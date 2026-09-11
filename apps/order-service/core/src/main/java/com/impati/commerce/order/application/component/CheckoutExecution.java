package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.CartClient;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OperationalAttention;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.CheckoutProgress.Stage;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.function.Supplier;

/** API와 워커가 공유하는 체크아웃 단계 실행기 (ADR-0018). */
@Component
@ConditionalOnBean({CartClient.class, InventoryClient.class, PaymentClient.class, ShippingClient.class})
public class CheckoutExecution {
    private static final Logger log = LoggerFactory.getLogger(CheckoutExecution.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final OrderRepository orderRepository;
    private final CheckoutProgressRepository progressRepository;
    private final CheckoutChanges checkoutChanges;
    private final CartClient cartClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final ShippingClient shippingClient;
    private final OperationalAttention operationalAttention;
    private final Clock clock;

    public CheckoutExecution(
            OrderRepository orderRepository,
            CheckoutProgressRepository progressRepository,
            CheckoutChanges checkoutChanges,
            CartClient cartClient,
            InventoryClient inventoryClient,
            PaymentClient paymentClient,
            ShippingClient shippingClient,
            OperationalAttention operationalAttention,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.progressRepository = progressRepository;
        this.checkoutChanges = checkoutChanges;
        this.cartClient = cartClient;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.shippingClient = shippingClient;
        this.operationalAttention = operationalAttention;
        this.clock = clock;
    }

    public void run(CheckoutProgress progress) {
        try {
            if (progress.stage() == Stage.COMPENSATING) {
                compensate(progress);
                return;
            }
            forward(progress);
        } catch (CheckoutChanges.LeaseLostException ignored) {
            log.info("checkout execution stopped after lease loss order={}", progress.orderId());
        } catch (CheckoutChanges.PersistenceFailure failure) {
            // 메모리 객체는 DB보다 한 단계 앞서 있을 수 있다. 저장을 더 시도하지 않고 임차가
            // 만료되게 두면 다음 실행자가 DB에 남은 단계부터 멱등하게 다시 시작한다.
            log.warn("checkout execution stopped after persistence failure order={}", progress.orderId(), failure);
        } catch (RuntimeException failure) {
            handleFailure(progress, failure);
        }
    }

    private void forward(CheckoutProgress progress) {
        var order = order(progress.orderId());
        while (true) {
            switch (progress.stage()) {
                case ACCEPTED -> {
                    retryOnce(() -> cartClient.checkout(
                            progress.memberId(), progress.orderId(), progress.expectedCartVersion()));
                    progress.advance(Stage.CART_DETACHED);
                    checkoutChanges.commit(progress, progress.leaseGeneration());
                }
                case CART_DETACHED -> {
                    var reservation = retryOnce(() -> inventoryClient.reserve(new ReserveInventoryRequest(
                            order.id(), order.lines().stream()
                                    .map(line -> new ReservationLine(line.skuId(), line.quantity())).toList())));
                    progress.reservation(reservation.id());
                    progress.advance(Stage.INVENTORY_RESERVED);
                    order.attachReservation(reservation.id());
                    checkoutChanges.commit(order, progress, progress.leaseGeneration());
                }
                case INVENTORY_RESERVED -> {
                    var payment = retryOnce(() -> paymentClient.authorizePayment(new AuthorizePaymentRequest(
                            order.id(), order.memberId(), order.total(), progress.paymentToken())));
                    progress.payment(payment.id());
                    progress.advance(Stage.PAYMENT_AUTHORIZED);
                    order.attachPayment(payment.id());
                    checkoutChanges.commit(order, progress, progress.leaseGeneration());
                }
                case PAYMENT_AUTHORIZED -> {
                    var shipment = retryOnce(() -> shippingClient.createShipment(new CreateShipmentRequest(
                            order.id(), order.memberId(), address(order.shippingAddress()))));
                    progress.shipment(shipment.id(), shipment.trackingNumber());
                    progress.advance(Stage.SHIPMENT_CREATED);
                    checkoutChanges.commit(progress, progress.leaseGeneration());
                }
                case SHIPMENT_CREATED -> {
                    progress.advance(Stage.CAPTURE_PENDING);
                    checkoutChanges.commit(progress, progress.leaseGeneration());
                }
                case CAPTURE_PENDING -> {
                    retryOnce(() -> paymentClient.capturePayment(require(progress.paymentId(), "payment")));
                    progress.advance(Stage.PAYMENT_CAPTURED);
                    checkoutChanges.commit(progress, progress.leaseGeneration());
                }
                case PAYMENT_CAPTURED -> {
                    order.markPaid();
                    order.attachShipment(
                            require(progress.shipmentId(), "shipment"),
                            require(progress.trackingNumber(), "tracking number"));
                    progress.advance(Stage.ORDER_CONFIRMED);
                    checkoutChanges.commit(order, progress, progress.leaseGeneration());
                }
                case ORDER_CONFIRMED -> {
                    retryOnce(() -> {
                        inventoryClient.commitReservation(require(progress.reservationId(), "reservation"));
                        return null;
                    });
                    progress.advance(Stage.INVENTORY_COMMITTED);
                    checkoutChanges.commit(progress, progress.leaseGeneration());
                }
                case INVENTORY_COMMITTED -> {
                    progress.succeed();
                    checkoutChanges.commit(progress, progress.leaseGeneration());
                }
                case COMPLETED, FAILED, ATTENTION_REQUIRED -> { return; }
                case COMPENSATING -> {
                    compensate(progress);
                    return;
                }
            }
        }
    }

    private void handleFailure(CheckoutProgress progress, RuntimeException failure) {
        if (failure instanceof CheckoutChanges.LeaseLostException) return;
        var code = failure instanceof DomainException domain ? domain.code() : "unexpected";
        switch (code) {
            case "cart_empty" -> startCompensation(progress, "CART_EMPTY", failure);
            case "cart_changed" -> startCompensation(progress, "CART_CHANGED", failure);
            case "out_of_stock" -> startCompensation(progress, "OUT_OF_STOCK", failure);
            case "payment_declined" -> startCompensation(progress, "PAYMENT_DECLINED", failure);
            case "outcome_unknown" -> {
                if (progress.stage().ordinal() >= Stage.PAYMENT_CAPTURED.ordinal()) {
                    retryLater(progress, failure);
                } else {
                    startCompensation(progress, "CHECKOUT_FAILED", failure);
                }
            }
            case "service_unavailable" -> retryLater(progress, failure);
            default -> attention(progress, failure);
        }
    }

    private void startCompensation(CheckoutProgress progress, String failureCode, RuntimeException failure) {
        var paymentMayBeCaptured = progress.stage().ordinal() >= Stage.CAPTURE_PENDING.ordinal();
        progress.compensate(failureCode, failure.getMessage(), paymentMayBeCaptured);
        checkoutChanges.commit(progress, progress.leaseGeneration());
        compensate(progress);
    }

    private void compensate(CheckoutProgress progress) {
        var failures = new ArrayList<RuntimeException>();
        var shipment = attempt(() -> shippingClient.shipmentForOrder(progress.orderId()).orElse(null), failures);
        if (shipment != null && !shipment.status().equals("CANCELLED")) {
            attempt(() -> shippingClient.cancelShipment(shipment.id()), failures);
        }

        var payment = progress.paymentId() == null
                ? attempt(() -> paymentClient.paymentForOrder(progress.orderId()).orElse(null), failures)
                : attempt(() -> paymentClient.payment(progress.paymentId()), failures);
        if (payment != null) {
            switch (payment.status()) {
                case "AUTHORIZED" -> attempt(() -> paymentClient.cancelPayment(payment.id()), failures);
                case "CAPTURED" -> {
                    progress.paymentCleanup("REFUNDING");
                    attempt(() -> paymentClient.refundPayment(payment.id()), failures);
                }
                case "CANCELLED", "REFUNDED" -> { }
                default -> failures.add(new IllegalStateException("unknown payment status " + payment.status()));
            }
        }

        var reservation = attempt(
                () -> inventoryClient.reservationForOrder(progress.orderId()).orElse(null), failures);
        if (reservation != null) {
            if (reservation.status().equals("RESERVED")) {
                attempt(() -> {
                    inventoryClient.releaseReservation(reservation.id());
                    return null;
                }, failures);
            } else if (reservation.status().equals("COMMITTED")) {
                failures.add(new IllegalStateException("failed checkout has committed inventory"));
            }
        }

        if (!failures.isEmpty()) {
            var failure = failures.getFirst();
            progress.retryLater(failure.getMessage(), now().plus(RETRY_DELAY));
            checkoutChanges.commit(progress, progress.leaseGeneration());
            log.warn("checkout compensation incomplete order={} failures={}", progress.orderId(), failures.size());
            return;
        }

        var order = order(progress.orderId());
        order.cancel(progress.failureCode());
        progress.fail();
        checkoutChanges.commit(order, progress, progress.leaseGeneration());
    }

    private void retryLater(CheckoutProgress progress, RuntimeException failure) {
        progress.retryLater(failure.getMessage(), now().plus(RETRY_DELAY));
        checkoutChanges.commit(progress, progress.leaseGeneration());
    }

    private void attention(CheckoutProgress progress, RuntimeException failure) {
        var stage = progress.stage().name();
        progress.attention(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        checkoutChanges.commit(progress, progress.leaseGeneration());
        operationalAttention.required(progress.orderId(), stage, progress.lastError());
    }

    private <T> T retryOnce(Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException first) {
            if (first instanceof DomainException domain
                    && !domain.code().equals("outcome_unknown")
                    && !domain.code().equals("service_unavailable")) {
                throw first;
            }
            if (!(first instanceof DomainException)) {
                throw first;
            }
            log.warn("checkout external call failed, retrying once cause={}", first.getMessage());
            return action.get();
        }
    }

    private <T> T attempt(Supplier<T> action, ArrayList<RuntimeException> failures) {
        try {
            return retryOnce(action);
        } catch (RuntimeException failure) {
            failures.add(failure);
            return null;
        }
    }

    private Order order(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("checkout order disappeared: " + orderId));
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("checkout has no " + field);
        return value;
    }

    private static com.impati.commerce.common.ApiContracts.AddressResponse address(Address address) {
        return new com.impati.commerce.common.ApiContracts.AddressResponse(
                address.id(), address.alias(), address.recipient(), address.phone(), address.line1(),
                address.city(), address.postalCode(), address.defaultAddress());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
