package com.impati.commerce.order.application.component;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;
import com.impati.commerce.order.application.model.CheckoutResult;
import com.impati.commerce.order.application.model.OrderDetails;
import com.impati.commerce.order.application.model.PurchaseQuoteDetails;
import com.impati.commerce.order.application.port.in.OrderUseCase;
import com.impati.commerce.order.application.port.out.CartClient;
import com.impati.commerce.order.application.port.out.CatalogClient;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.MemberClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.CheckoutRequestFingerprint;
import com.impati.commerce.order.domain.IdempotencyKey;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.order.domain.PurchasePricing;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 체크아웃 접수와 주문 조회를 제공한다. 단계 실행은 API와 워커가 공유하는 {@link CheckoutExecution}이 맡는다.
 */
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
    private final Clock clock;

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
            ShippingClient shippingClient,
            Clock clock
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
        this.clock = clock;
    }

    @Override
    public CheckoutResult checkoutConfirmed(
            String memberId,
            IdempotencyKey idempotencyKey,
            String paymentToken,
            String addressId,
            String quoteId
    ) {
        validateQuoteId(quoteId);
        validatePaymentToken(paymentToken);
        var fingerprint = confirmedRequestFingerprint(paymentToken, addressId, quoteId);
        var existing = progressRepository.findByMemberAndKey(memberId, idempotencyKey);
        if (existing.isPresent()) {
            ensureSameRequest(existing.get(), fingerprint);
            return result(existing.get(), false);
        }

        var cart = cartClient.cart(memberId);
        if (cart.lines().isEmpty()) {
            throw DomainException.cartEmpty("cart is empty");
        }

        var orderLines = loadPricedOrderLines(cart);
        ensureQuoteMatches(memberId, cart.version(), orderLines, quoteId);
        var member = memberClient.member(memberId);
        var address = OrderMapper.toAddress(selectAddress(member.addresses(), addressId));
        var orderId = Ids.newId("ord");
        var order = Order.create(orderId, memberId, orderLines, address, clock);
        var progress = new CheckoutProgress(orderId, memberId, idempotencyKey, fingerprint, paymentToken, cart.version());

        if (!checkoutChanges.create(order, progress)) {
            var raced = progressRepository.findByMemberAndKey(memberId, idempotencyKey)
                    .orElseThrow(() -> DomainException.conflict("checkout acceptance raced without a result"));
            ensureSameRequest(raced, fingerprint);
            return result(raced, false);
        }

        progressRepository.claim(orderId, CheckoutRecoveryExecutor.LEASE_DURATION).ifPresent(checkoutExecution::run);
        return result(progressRepository.findByOrderId(orderId).orElseThrow(), true);
    }

    @Override
    public PurchaseQuoteDetails quote(String memberId, long expectedVersion) {
        var cart = cartClient.cart(memberId);
        if (cart.version() != expectedVersion) {
            throw DomainException.cartChanged("cart changed during quote lookup");
        }
        var lines = loadPricedOrderLines(cart);
        var quoteLines = lines.stream().map(line -> new PurchaseQuoteDetails.Line(
                line.skuId(), line.quantity(), line.unitPrice(), line.lineTotal())).toList();
        return new PurchaseQuoteDetails(
                PurchasePricing.quoteId(memberId, cart.version(), lines),
                cart.version(),
                quoteLines,
                PurchasePricing.total(lines)
        );
    }

    private List<OrderLine> loadPricedOrderLines(CartResponse cart) {
        return cart.lines().stream().map(line -> {
            var sku = catalogClient.sku(line.skuId());
            var product = catalogClient.product(sku.productId());
            return new OrderLine(sku.id(), product.id(), product.name(), sku.name(), line.quantity(), sku.price());
        }).toList();
    }

    private static void validateQuoteId(String quoteId) {
        if (quoteId == null || !quoteId.matches("[a-f0-9]{64}")) {
            throw DomainException.validation("a server purchase quote is required");
        }
    }

    private static CheckoutRequestFingerprint confirmedRequestFingerprint(
            String paymentToken,
            String addressId,
            String quoteId
    ) {
        var paymentAndAddress = CheckoutRequestFingerprint.from(paymentToken, addressId);
        return CheckoutRequestFingerprint.from(paymentAndAddress.value(), quoteId);
    }

    private static void ensureQuoteMatches(
            String memberId,
            long cartVersion,
            List<OrderLine> lines,
            String confirmedQuoteId
    ) {
        var currentQuoteId = PurchasePricing.quoteId(memberId, cartVersion, lines);
        if (!confirmedQuoteId.equals(currentQuoteId)) {
            throw DomainException.quoteChanged("구매 내용이나 금액이 변경됐습니다. 새 견적을 확인해주세요.");
        }
    }

    private CheckoutResult result(CheckoutProgress progress, boolean newlyAccepted) {
        return result(order(progress.orderId()), progress, newlyAccepted);
    }

    private CheckoutResult result(Order order, CheckoutProgress progress, boolean newlyAccepted) {
        PaymentResponse payment = null;
        ShipmentResponse shipment = null;
        if (progress.outcome() == CheckoutProgress.Outcome.SUCCEEDED) {
            try {
                if (progress.paymentId() != null) {
                    payment = paymentClient.payment(progress.paymentId());
                }
                shipment = shippingClient.shipmentForOrder(progress.orderId()).orElse(null);
            } catch (RuntimeException ignored) {
                // 구매 결과는 주문 DB에 확정돼 있다. 응답 장식 조회 실패가 성공을 뒤집지 않는다.
            }
        }
        return new CheckoutResult(OrderMapper.toDetails(order, progress), payment, shipment, newlyAccepted);
    }

    @Override
    public CheckoutResult getCheckoutResultOwned(String memberId, String orderId) {
        var order = order(orderId);
        if (!order.memberId().equals(memberId)) {
            throw DomainException.notFound("order not found");
        }
        var progress = progressRepository.findByOrderId(orderId)
                .orElseThrow(() -> DomainException.notFound("checkout result not found"));
        if (progress.outcome() == CheckoutProgress.Outcome.SUCCEEDED) {
            var payment = progress.paymentId() == null ? null : paymentClient.payment(progress.paymentId());
            var shipment = shippingClient.shipmentForOrder(progress.orderId()).orElseThrow(() ->
                    DomainException.unavailable("completed checkout shipment is temporarily unavailable"));
            return new CheckoutResult(OrderMapper.toDetails(order, progress), payment, shipment, false);
        }
        return result(order, progress, false);
    }

    @Override
    public OrderDetails markDelivered(String orderId) {
        var order = order(orderId);
        order.markDelivered();
        orderChanges.commit(order);
        return OrderMapper.toDetails(order, progressRepository.findByOrderId(orderId).orElse(null));
    }

    private AddressResponse selectAddress(List<AddressResponse> addresses, String addressId) {
        if (addresses.isEmpty()) {
            throw DomainException.validation("member has no delivery address");
        }
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

    private static void validatePaymentToken(String paymentToken) {
        if (paymentToken == null || paymentToken.isBlank() || paymentToken.length() > 255) {
            throw DomainException.validation("payment token is required and must be at most 255 characters");
        }
    }

    private static void ensureSameRequest(CheckoutProgress existing, CheckoutRequestFingerprint fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw DomainException.conflict("idempotency key was already used for a different checkout request");
        }
    }
}
