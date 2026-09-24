package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.common.ApiContracts.OrderDetailResponse;
import com.impati.commerce.common.ApiContracts.OrderPageResponse;
import com.impati.commerce.common.ApiContracts.OrderResponse;
import com.impati.commerce.common.ApiContracts.OrderCancellationResponse;
import com.impati.commerce.order.application.port.in.OrderCancellationUseCase;
import com.impati.commerce.order.application.model.OrderQueryKey;
import com.impati.commerce.order.application.port.in.OrderHistoryUseCase;
import com.impati.commerce.order.application.port.in.OrderUseCase;
import com.impati.commerce.order.domain.IdempotencyKey;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 API.
 *
 * <p>회원 신원은 {@code X-Member-Id} 헤더로 받는다. 주문 조회는 요청자 소유인지 확인한다 —
 * orderId만으로 조회되면 남의 주문 내역과 배송지가 노출된다.
 */
@RestController
@RequestMapping
public class OrderController {

    private final OrderUseCase orderUseCase;
    private final OrderHistoryUseCase orderHistoryUseCase;
    private final OrderCancellationUseCase orderCancellationUseCase;

    public OrderController(OrderUseCase orderUseCase, OrderHistoryUseCase orderHistoryUseCase,
            OrderCancellationUseCase orderCancellationUseCase) {
        this.orderUseCase = orderUseCase;
        this.orderHistoryUseCase = orderHistoryUseCase;
        this.orderCancellationUseCase = orderCancellationUseCase;
    }

    @PostMapping("/checkouts/confirmed")
    ResponseEntity<CheckoutResponse> checkoutConfirmed(
            @RequestHeader("X-Member-Id") String memberId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody ConfirmedCheckoutRequest request
    ) {
        var result = orderUseCase.checkoutConfirmed(
                memberId, new IdempotencyKey(key), request.paymentToken(), request.addressId(), request.quoteId(), request.addressConfirmationToken());
        var response = OrderResponseMapper.from(result);
        if ("PROCESSING".equals(result.order().checkoutStatus())) {
            return ResponseEntity.accepted().body(response);
        }
        if (result.newlyAccepted()) {
            return ResponseEntity.status(201).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/{orderId}")
    OrderDetailResponse order(@RequestHeader("X-Member-Id") String memberId, @PathVariable String orderId) {
        return OrderHistoryResponseMapper.from(orderHistoryUseCase.getOwned(memberId, orderId));
    }

    @GetMapping("/orders")
    OrderPageResponse orders(@RequestHeader("X-Member-Id") String memberId,
                             @RequestParam(required = false) String cursor,
                             @RequestParam(defaultValue = "20") int size
    ) {
        return OrderHistoryResponseMapper.from(orderHistoryUseCase.getOrders(OrderQueryKey.of(memberId, cursor, size)));
    }

    @GetMapping("/orders/{orderId}/checkout-result")
    CheckoutResponse checkoutResult(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable String orderId
    ) {
        return OrderResponseMapper.from(orderUseCase.getCheckoutResultOwned(memberId, orderId));
    }

    @PostMapping("/orders/{orderId}/cancellation")
    ResponseEntity<OrderCancellationResponse> cancel(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable String orderId
    ) {
        var result = orderCancellationUseCase.cancel(memberId, orderId);
        var response = new OrderCancellationResponse(result.orderId(), result.status());
        return "COMPLETED".equals(result.status())
                ? ResponseEntity.ok(response)
                : ResponseEntity.accepted().body(response);
    }

}
