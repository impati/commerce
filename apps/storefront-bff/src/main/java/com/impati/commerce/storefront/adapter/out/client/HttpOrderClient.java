package com.impati.commerce.storefront.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CheckoutResponse;
import com.impati.commerce.common.ApiContracts.ConfirmedCheckoutRequest;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.RestClientFactory;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.storefront.application.port.out.OrderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpOrderClient implements OrderClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;
    public HttpOrderClient(RestClientFactory factory, ServiceCallExecutor calls, @Value("${clients.order.url}") String url) {
        this.restClient = factory.forBaseUrl(url);
        this.calls = calls;
    }
    public PurchaseQuoteResponse quote(String memberId, long version) {
        return calls.query("storefront purchase quote", () -> restClient.get().uri(builder -> builder.path("/internal/purchase-quotes").queryParam("cartVersion", version).build())
                .header("X-Member-Id", memberId).retrieve().body(PurchaseQuoteResponse.class), error -> {
            if (error.hasCode("cart_changed")) {
                throw DomainException.cartChanged("장바구니가 변경됐습니다. 다시 확인해주세요.");
            }
        });
    }
    public ResponseEntity<CheckoutResponse> checkout(String memberId, String key, ConfirmedCheckoutRequest request) {
        return calls.command("storefront confirmed checkout", () -> restClient.post().uri("/checkouts/confirmed")
                .header("X-Member-Id", memberId).header("Idempotency-Key", key).body(request).retrieve().toEntity(CheckoutResponse.class), error -> {
            if (error.hasCode("quote_changed")) {
                throw new DomainException("quote_changed", "구매 내용이나 금액이 변경됐습니다. 새 견적을 확인해주세요.", 409);
            }
            if (error.hasCode("cart_changed")) {
                throw DomainException.cartChanged("장바구니가 변경됐습니다. 다시 확인해주세요.");
            }
            if (error.hasCode("cart_empty")) {
                throw DomainException.cartEmpty("장바구니가 비어 있습니다.");
            }
            if (error.hasCode("validation_error")) {
                throw DomainException.validation("주문 요청을 확인해주세요.");
            }
            if (error.hasCode("conflict")) {
                throw DomainException.conflict("같은 주문 요청의 내용이 다릅니다.");
            }
            if (error.hasCode("not_found")) {
                throw DomainException.notFound("주문 정보를 찾을 수 없습니다.");
            }
        });
    }
}
