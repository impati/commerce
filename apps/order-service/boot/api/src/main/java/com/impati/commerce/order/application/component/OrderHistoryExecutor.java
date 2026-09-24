package com.impati.commerce.order.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.model.OrderCursor;
import com.impati.commerce.order.application.model.OrderHistoryDetail;
import com.impati.commerce.order.application.port.in.OrderHistoryUseCase;
import com.impati.commerce.order.application.model.OrderPage;
import com.impati.commerce.order.application.model.OrderQueryKey;
import com.impati.commerce.order.application.model.OrderSummary;
import com.impati.commerce.order.application.model.OrderTimelineEntry;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CancellationProgress;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import java.time.ZoneOffset;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 체크아웃 실행과 분리된 고객용 읽기 유스케이스. 외부 장식 조회 없이 확정된 사실을 읽는다. */
@Component
@Transactional(readOnly = true)
public class OrderHistoryExecutor implements OrderHistoryUseCase {
    private final OrderRepository orderRepository;
    private final CheckoutProgressRepository checkoutProgressRepository;
    private final OrderEventRepository orderEventRepository;
    private final CancellationProgressRepository cancellationProgressRepository;
    private final ShippingClient shippingClient;

    public OrderHistoryExecutor(OrderRepository orderRepository, CheckoutProgressRepository checkoutProgressRepository,
            OrderEventRepository orderEventRepository, CancellationProgressRepository cancellationProgressRepository,
            ShippingClient shippingClient) {
        this.orderRepository = orderRepository;
        this.checkoutProgressRepository = checkoutProgressRepository;
        this.orderEventRepository = orderEventRepository;
        this.cancellationProgressRepository = cancellationProgressRepository;
        this.shippingClient = shippingClient;
    }

    @Override
    public OrderPage getOrders(OrderQueryKey query) {
        var rows = orderRepository.findBy(query.memberId(), query.cursor(), query.size() + 1);
        var page = rows.stream().limit(query.size()).toList();
        var progress = checkoutProgressRepository.findByOrderIds(query.memberId(), page.stream().map(Order::id).toList())
                .stream().collect(Collectors.toMap(CheckoutProgress::orderId, Function.identity()));
        var cancellations = cancellationProgressRepository.findByOrderIds(
                        query.memberId(), page.stream().map(Order::id).toList())
                .stream().collect(Collectors.toMap(CancellationProgress::orderId, Function.identity()));
        var items = page.stream().map(order -> summary(
                order, progress.get(order.id()), cancellations.get(order.id()))).toList();
        var cursor = rows.size() > query.size()
                ? new OrderCursor(page.getLast().createdAt(), page.getLast().id()).encode() : null;
        return new OrderPage(items, cursor);
    }

    @Override
    public OrderHistoryDetail getOwned(String memberId, String orderId) {
        var order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> DomainException.notFound("order not found"));
        // 소유권 확인 전에 사건이나 진행 정보를 읽지 않는다.
        var progress = checkoutProgressRepository.findByOrderIds(memberId, java.util.List.of(orderId))
                .stream().findFirst().orElse(null);
        var cancellation = cancellationProgressRepository.findByOrderIds(memberId, java.util.List.of(orderId))
                .stream().findFirst().orElse(null);
        var state = customerState(order, progress);
        var events = orderEventRepository.findByOrderIdAndMemberId(orderId, memberId);
        var timeline = events.stream()
                // 보상이 끝나기 전에 내부 취소를 구매 실패라는 확정 결과로 제시하지 않는다.
                .filter(event -> event.type() != OrderEventType.CHECKOUT_FAILED || "FAILED".equals(state))
                .map(event -> new OrderTimelineEntry(event.type().name(), event.occurredAt().atOffset(ZoneOffset.UTC)))
                .toList();
        var shipmentEvents = events.stream().filter(event -> event.type().name().startsWith("SHIPMENT_")
                || event.type() == OrderEventType.ORDER_DELIVERED).toList();
        var latestShipmentPayload = shipmentEvents.stream().map(event -> event.payload())
                .filter(payload -> payload.get("shipmentStatus") != null).reduce((first, last) -> last)
                .orElse(java.util.Map.of());
        var shipmentStatus = latestShipmentPayload.getOrDefault("shipmentStatus",
                order.shipmentId() == null ? null : "READY");
        var carrierCode = latestShipmentPayload.get("carrierCode");
        var carrierName = latestShipmentPayload.get("carrierName");
        var trackingNumber = latestShipmentPayload.get("trackingNumber");
        var details = OrderMapper.toDetails(order);
        return new OrderHistoryDetail(order.id(), order.createdAt().atOffset(ZoneOffset.UTC), state,
                customerOrderStatus(order, state), cancellationStatus(cancellation),
                cancellable(order, state, cancellation),
                details.lines(), details.priceBreakdown(), details.shippingAddress(),
                shipmentStatus, carrierCode, carrierName, trackingNumber, timeline);
    }

    private static OrderSummary summary(
            Order order,
            CheckoutProgress progress,
            CancellationProgress cancellation
    ) {
        var representative = order.lines().getFirst();
        var additional = (int) order.lines().stream().map(line -> line.productId()).distinct().count() - 1;
        var quantity = order.lines().stream().mapToInt(line -> line.quantity()).sum();
        var state = customerState(order, progress);
        return new OrderSummary(order.id(), order.createdAt().atOffset(ZoneOffset.UTC), representative.productName(),
                representative.skuName(), additional, quantity, OrderMapper.toDetails(order.priceBreakdown()), state,
                customerOrderStatus(order, state), cancellationStatus(cancellation));
    }

    private static String customerState(Order order, CheckoutProgress progress) {
        if (progress != null) {
            return switch (progress.stage()) {
                case COMPLETED -> "SUCCEEDED";
                case FAILED -> "FAILED";
                case ATTENTION_REQUIRED -> "CHECKING";
                default -> "PROCESSING";
            };
        }
        // 체크아웃 진행 기록 없이 도메인에서 생성된 주문도 내부 상태를 그대로 노출하지 않는다.
        return switch (order.status()) {
            case PAID, FULFILLING, DELIVERED -> "SUCCEEDED";
            case CANCELLED -> "FAILED";
            case CREATED -> "PROCESSING";
        };
    }

    private static String customerOrderStatus(Order order, String state) {
        return "SUCCEEDED".equals(state) ? order.status().name() : null;
    }

    private static String cancellationStatus(CancellationProgress progress) {
        return progress == null ? "NONE" : progress.customerStatus().name();
    }

    private boolean cancellable(Order order, String checkoutState, CancellationProgress cancellation) {
        if (!"SUCCEEDED".equals(checkoutState) || cancellation != null
                || !order.canStartCustomerCancellation()) {
            return false;
        }
        try {
            return shippingClient.shipmentForOrder(order.id())
                    .map(shipment -> shipment.status().equals("READY")
                            || shipment.status().equals("AWAITING_PICKUP"))
                    .orElse(false);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
