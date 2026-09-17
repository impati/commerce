package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.CheckoutRequest;
import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteLineResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.order.application.port.in.OrderUseCase;
import com.impati.commerce.order.domain.IdempotencyKey;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면 견적 조회와 기존 내부 구매 호출. 브라우저의 접수는 확인된 견적을 요구한다.
 */
@RestController
public class InternalPurchaseController {

    private final OrderUseCase orderUseCase;

    public InternalPurchaseController(OrderUseCase orderUseCase) {
        this.orderUseCase = orderUseCase;
    }
    @PostMapping("/internal/checkouts")
    ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader("X-Member-Id") String memberId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CheckoutRequest request
    ) {
        var result = orderUseCase.checkout(memberId, new IdempotencyKey(idempotencyKey), request.paymentToken(), request.addressId());
        var response = OrderResponseMapper.from(result);
        if ("PROCESSING".equals(result.order().checkoutStatus())) {
            return ResponseEntity.accepted().body(response);
        }

        return ResponseEntity.status(result.newlyAccepted() ? 201 : 200).body(response);
    }

    @GetMapping("/internal/purchase-quotes")
    PurchaseQuoteResponse quote(@RequestHeader("X-Member-Id") String memberId, @RequestParam long cartVersion) {
        var quote = orderUseCase.quote(memberId, cartVersion);
        return new PurchaseQuoteResponse(quote.id(), quote.cartVersion(),
                quote.lines().stream().map(line -> new PurchaseQuoteLineResponse(
                        line.skuId(), line.quantity(), line.unitPrice(), line.lineTotal())).toList(), quote.total());
    }

}
