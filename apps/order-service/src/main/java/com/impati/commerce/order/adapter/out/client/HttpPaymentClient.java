package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CapturePaymentRequest;
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
    public PaymentResponse capturePayment(CapturePaymentRequest request) {
        try {
            return restClient.post().uri("/payments/capture").body(request).retrieve().body(PaymentResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 402) {
                throw DomainException.paymentDeclined("payment was declined by issuer");
            }
            throw DomainException.conflict("payment service error: " + exception.getStatusText());
        }
    }
}
