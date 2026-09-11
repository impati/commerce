package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;
import com.impati.commerce.order.application.port.in.CheckoutResult;
import com.impati.commerce.order.application.port.in.OrderDetails;
import com.impati.commerce.order.application.port.in.OrderUseCase;
import com.impati.commerce.order.application.port.out.CartClient;
import com.impati.commerce.order.application.port.out.CatalogClient;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.MemberClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 체크아웃 접수와 주문 조회를 제공한다. 단계 실행은 API와 워커가 공유하는 {@link CheckoutExecution}이 맡는다. */
@Component
public class OrderExecutor implements OrderUseCase {
    private final OrderRepository orderRepository;
    private final CheckoutProgressRepository progressRepository;
    private final CheckoutChanges checkoutChanges;
    private final OrderChanges orderChanges;
    private final CheckoutExecution checkoutExecution;
    private final MemberClient memberClient;
    private final CartClient cartClient;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;
    private final ShippingClient shippingClient;

    public OrderExecutor(
            OrderRepository orderRepository,
            CheckoutProgressRepository progressRepository,
            CheckoutChanges checkoutChanges,
            OrderChanges orderChanges,
            CheckoutExecution checkoutExecution,
            MemberClient memberClient,
            CartClient cartClient,
            CatalogClient catalogClient,
            PaymentClient paymentClient,
            ShippingClient shippingClient
    ) {
        this.orderRepository = orderRepository;
        this.progressRepository = progressRepository;
        this.checkoutChanges = checkoutChanges;
        this.orderChanges = orderChanges;
        this.checkoutExecution = checkoutExecution;
        this.memberClient = memberClient;
        this.cartClient = cartClient;
        this.catalogClient = catalogClient;
        this.paymentClient = paymentClient;
        this.shippingClient = shippingClient;
    }

    @Override
    public CheckoutResult checkout(
            String memberId,
            String idempotencyKey,
            String paymentToken,
            String addressId
    ) {
        validateKey(idempotencyKey);
        var fingerprint = fingerprint(paymentToken, addressId);
        var existing = progressRepository.findByMemberAndKey(memberId, idempotencyKey);
        if (existing.isPresent()) {
            ensureSameRequest(existing.get(), fingerprint);
            return result(existing.get(), false);
        }

        var member = memberClient.member(memberId);
        var address = OrderMapper.toAddress(selectAddress(member.addresses(), addressId));
        var cart = cartClient.cart(memberId);
        if (cart.lines().isEmpty()) throw DomainException.cartEmpty("cart is empty");

        var orderLines = cart.lines().stream().map(line -> {
            var sku = catalogClient.sku(line.skuId());
            var product = catalogClient.product(sku.productId());
            return new OrderLine(sku.id(), product.id(), product.name(), sku.name(), line.quantity(), sku.price());
        }).toList();
        var orderId = Ids.newId("ord");
        var order = Order.create(orderId, memberId, orderLines, address);
        var progress = new CheckoutProgress(
                orderId, memberId, idempotencyKey, fingerprint, paymentToken, cart.version());

        if (!checkoutChanges.create(order, progress)) {
            var raced = progressRepository.findByMemberAndKey(memberId, idempotencyKey)
                    .orElseThrow(() -> DomainException.conflict("checkout acceptance raced without a result"));
            ensureSameRequest(raced, fingerprint);
            return result(raced, false);
        }

        progressRepository.claim(orderId, CheckoutRecoveryExecutor.LEASE_DURATION)
                .ifPresent(checkoutExecution::run);
        return result(progressRepository.findByOrderId(orderId).orElseThrow(), true);
    }

    private CheckoutResult result(CheckoutProgress progress, boolean newlyAccepted) {
        var order = order(progress.orderId());
        PaymentResponse payment = null;
        ShipmentResponse shipment = null;
        if (progress.outcome() == CheckoutProgress.Outcome.SUCCEEDED) {
            try {
                if (progress.paymentId() != null) payment = paymentClient.payment(progress.paymentId());
                shipment = shippingClient.shipmentForOrder(progress.orderId()).orElse(null);
            } catch (RuntimeException ignored) {
                // 구매 결과는 주문 DB에 확정돼 있다. 응답 장식 조회 실패가 성공을 뒤집지 않는다.
            }
        }
        return new CheckoutResult(OrderMapper.toDetails(order, progress), payment, shipment, newlyAccepted);
    }

    @Override
    public OrderDetails getOwned(String memberId, String orderId) {
        var order = order(orderId);
        if (!order.memberId().equals(memberId)) throw DomainException.notFound("order not found");
        return OrderMapper.toDetails(order, progressRepository.findByOrderId(orderId).orElse(null));
    }

    @Override
    public OrderDetails markDelivered(String orderId) {
        var order = order(orderId);
        order.markDelivered();
        orderChanges.commit(order);
        return OrderMapper.toDetails(order, progressRepository.findByOrderId(orderId).orElse(null));
    }

    private AddressResponse selectAddress(List<AddressResponse> addresses, String addressId) {
        if (addresses.isEmpty()) throw DomainException.validation("member has no delivery address");
        if (addressId == null || addressId.isBlank()) {
            return addresses.stream().filter(AddressResponse::defaultAddress).findFirst().orElse(addresses.getFirst());
        }
        return addresses.stream().filter(address -> address.id().equals(addressId)).findFirst()
                .orElseThrow(() -> DomainException.validation("address does not belong to member"));
    }

    private Order order(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> DomainException.notFound("order not found"));
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw DomainException.validation("Idempotency-Key is required and must be at most 128 characters");
        }
    }

    private static void ensureSameRequest(CheckoutProgress existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw DomainException.conflict("idempotency key was already used for a different checkout request");
        }
    }

    private static String fingerprint(String paymentToken, String addressId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var source = String.valueOf(paymentToken) + "\u0000" + String.valueOf(addressId);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
