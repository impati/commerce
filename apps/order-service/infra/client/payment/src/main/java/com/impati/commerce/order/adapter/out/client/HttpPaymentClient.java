package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.RefundPaymentRequest;
import com.impati.commerce.common.ApiContracts.RefundPaymentResponse;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.order.application.port.out.PaymentClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

@Component
public class HttpPaymentClient implements PaymentClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpPaymentClient(RestClient paymentRestClient, ServiceCallExecutor calls) {
        this.restClient = paymentRestClient;
        this.calls = calls;
    }

    /**
     * HTTP 상태를 도메인 언어로 옮긴다. 402는 결제 거절이고 그 외 응답은 결제 시스템 오류다.
     *
     * <p>응답을 받지 못한 경우는 다르다. 요청은 갔고 상대가 처리를 마쳤을 수 있으므로
     * 실패가 아니라 <b>결과 불명</b>이다. 이 구분이 없으면 호출자가 모르는 것을 실패로
     * 단정해 이미 일어난 일을 되돌린다.
     */
    @Override
    public PaymentResponse authorizePayment(AuthorizePaymentRequest request) {
        return calls.command("payment authorization", () -> restClient.post()
                    .uri("/internal/payments/authorize")
                    .body(request)
                    .retrieve()
                    .body(PaymentResponse.class), error -> {
            if (error.hasCode("payment_declined")) {
                throw DomainException.paymentDeclined("payment was declined by issuer");
            }
            if (error.hasCode("conflict")) {
                throw DomainException.conflict("payment request conflicts with the existing payment");
            }
        });
    }

    @Override
    public PaymentResponse capturePayment(String paymentId) {
        return post(paymentId, "capture");
    }

    @Override
    public PaymentResponse cancelPayment(String paymentId) {
        return post(paymentId, "cancel");
    }

    @Override
    public PaymentResponse refundPayment(String paymentId) {
        return post(paymentId, "refund");
    }

    @Override
    public RefundPaymentResponse refundReturn(String paymentId, String returnId, Money amount) {
        return calls.command("return refund", () -> restClient.post()
                .uri("/internal/payments/{paymentId}/refunds", paymentId)
                .body(new RefundPaymentRequest(returnId, amount)).retrieve()
                .body(RefundPaymentResponse.class));
    }

    @Override
    public Optional<RefundPaymentResponse> returnRefund(String returnId) {
        return calls.optionalQuery("return refund lookup", () -> restClient.get()
                .uri("/internal/payments/refunds/{returnId}", returnId).retrieve()
                .body(RefundPaymentResponse.class));
    }

    /**
     * 조회는 부수효과가 없으므로 실패를 <b>결과 불명</b>이 아니라 <b>지금 응답하지 못함</b>으로
     * 옮긴다. 아무 일도 일어나지 않은 것이 분명하므로 그대로 다시 시도해도 안전하다.
     */
    @Override
    public PaymentResponse payment(String paymentId) {
        return calls.query("payment lookup", () -> restClient.get()
                    .uri("/internal/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(PaymentResponse.class), error -> {
            if (error.hasCode("not_found")) {
                throw DomainException.notFound("payment not found: " + paymentId);
            }
        });
    }

    @Override
    public Optional<PaymentResponse> paymentForOrder(String orderId) {
        return calls.optionalQuery("order payment lookup", () -> restClient.get()
                .uri("/internal/payments/orders/{orderId}", orderId)
                .retrieve()
                .body(PaymentResponse.class));
    }

    /**
     * 승인 이후의 호출은 거절될 수 없다. 이미 확보된 대금을 다루는 것이므로 발급사가 다시
     * 판단하지 않는다. 실패는 전부 결제 시스템 오류다.
     */
    private PaymentResponse post(String paymentId, String action) {
        return calls.command("payment " + action, () -> restClient.post()
                    .uri("/internal/payments/{paymentId}/{action}", paymentId, action)
                    .retrieve()
                    .body(PaymentResponse.class), error -> {
            if (error.hasCode("conflict")) {
                throw DomainException.conflict("payment transition conflicts with its current state");
            }
        });
    }
}
