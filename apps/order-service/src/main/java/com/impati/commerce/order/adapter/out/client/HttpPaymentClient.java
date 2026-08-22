package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.PaymentClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpPaymentClient implements PaymentClient {
    private final RestClient restClient;

    public HttpPaymentClient(RestClient paymentRestClient) {
        this.restClient = paymentRestClient;
    }

    /** HTTP 상태를 도메인 언어로 옮긴다. 402는 결제 거절이고 그 외는 결제 시스템 오류다. */
    @Override
    public PaymentResponse authorizePayment(AuthorizePaymentRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/payments/authorize")
                    .body(request)
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 402) {
                throw DomainException.paymentDeclined("payment was declined by issuer");
            }
            throw DomainException.conflict("payment service error: " + exception.getStatusText());
        }
    }

    @Override
    public PaymentResponse capturePayment(String paymentId) {
        return post(paymentId, "capture");
    }

    @Override
    public PaymentResponse cancelPayment(String paymentId) {
        return post(paymentId, "cancel");
    }

    /**
     * 승인 이후의 호출은 거절될 수 없다. 이미 확보된 대금을 다루는 것이므로 발급사가 다시
     * 판단하지 않는다. 실패는 전부 결제 시스템 오류다.
     */
    private PaymentResponse post(String paymentId, String action) {
        try {
            return restClient.post()
                    .uri("/internal/payments/{paymentId}/{action}", paymentId, action)
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (RestClientResponseException exception) {
            throw DomainException.conflict("payment service error: " + exception.getStatusText());
        }
    }
}
