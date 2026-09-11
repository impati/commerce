package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.PaymentClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.util.Optional;

@Component
public class HttpPaymentClient implements PaymentClient {
    private final RestClient restClient;

    public HttpPaymentClient(RestClient paymentRestClient) {
        this.restClient = paymentRestClient;
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
            if (exception.getStatusCode().value() == 409) {
                throw DomainException.conflict("payment request conflicts with the existing payment");
            }
            throw DomainException.unavailable("payment service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.outcomeUnknown("payment authorization outcome unknown: " + exception.getMessage());
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

    @Override
    public PaymentResponse refundPayment(String paymentId) {
        return post(paymentId, "refund");
    }

    /**
     * 조회는 부수효과가 없으므로 실패를 <b>결과 불명</b>이 아니라 <b>지금 응답하지 못함</b>으로
     * 옮긴다. 아무 일도 일어나지 않은 것이 분명하므로 그대로 다시 시도해도 안전하다.
     */
    @Override
    public PaymentResponse payment(String paymentId) {
        try {
            return restClient.get()
                    .uri("/internal/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw DomainException.notFound("payment not found: " + paymentId);
            }
            throw DomainException.unavailable("payment service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.unavailable("payment lookup failed: " + exception.getMessage());
        }
    }

    @Override
    public Optional<PaymentResponse> paymentForOrder(String orderId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/payments/orders/{orderId}", orderId)
                    .retrieve().body(PaymentResponse.class));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw DomainException.unavailable("payment service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.unavailable("payment lookup failed: " + exception.getMessage());
        }
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
            if (exception.getStatusCode().value() == 409) {
                throw DomainException.conflict("payment transition conflicts with its current state");
            }
            throw DomainException.unavailable("payment service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.outcomeUnknown("payment " + action + " outcome unknown: " + exception.getMessage());
        }
    }
}
