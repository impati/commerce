package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.ShippingClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpShippingClient implements ShippingClient {
    private final RestClient restClient;

    public HttpShippingClient(RestClient shippingRestClient) {
        this.restClient = shippingRestClient;
    }

    @Override
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/shipments")
                    .body(request)
                    .retrieve()
                    .body(ShipmentResponse.class);
        } catch (RestClientResponseException exception) {
            throw shippingError(exception);
        }
    }

    @Override
    public ShipmentResponse cancelShipment(String shipmentId) {
        try {
            return restClient.post()
                    .uri("/internal/shipments/{shipmentId}/cancel", shipmentId)
                    .retrieve()
                    .body(ShipmentResponse.class);
        } catch (RestClientResponseException exception) {
            throw shippingError(exception);
        }
    }

    /** HTTP 상태를 도메인 언어로 옮긴다. 이걸 하지 않으면 프로토콜 예외가 응용 계층까지 올라간다. */
    private static DomainException shippingError(RestClientResponseException exception) {
        return DomainException.conflict("shipping service error: " + exception.getStatusText());
    }
}
