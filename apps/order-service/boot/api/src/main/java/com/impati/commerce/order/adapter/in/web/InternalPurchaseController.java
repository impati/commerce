package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.order.application.port.in.OrderUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면 표시를 위한 서버 견적 조회. 주문 접수는 OrderController가 확인된 견적으로 받는다.
 */
@RestController
public class InternalPurchaseController {

    private final OrderUseCase orderUseCase;

    public InternalPurchaseController(OrderUseCase orderUseCase) {
        this.orderUseCase = orderUseCase;
    }

    @GetMapping("/internal/purchase-quotes")
    PurchaseQuoteResponse quote(@RequestHeader("X-Member-Id") String memberId, @RequestParam long cartVersion) {
        var quote = orderUseCase.quote(memberId, cartVersion);
        return PurchaseQuoteResponseMapper.from(quote);
    }
}
