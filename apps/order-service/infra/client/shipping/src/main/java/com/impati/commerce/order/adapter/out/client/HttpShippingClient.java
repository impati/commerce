package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.ShippingClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.util.Optional;

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
        } catch (ResourceAccessException exception) {
            throw DomainException.outcomeUnknown("shipment creation outcome unknown: " + exception.getMessage());
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
        } catch (ResourceAccessException exception) {
            throw DomainException.outcomeUnknown("shipment cancellation outcome unknown: " + exception.getMessage());
        }
    }

    @Override
    public Optional<ShipmentResponse> shipmentForOrder(String orderId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/shipments/orders/{orderId}", orderId)
                    .retrieve().body(ShipmentResponse.class));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw shippingError(exception);
        } catch (ResourceAccessException exception) {
            throw DomainException.unavailable("shipment lookup failed: " + exception.getMessage());
        }
    }

    /** HTTP 상태를 도메인 언어로 옮긴다. 이걸 하지 않으면 프로토콜 예외가 응용 계층까지 올라간다. */
    private static DomainException shippingError(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 409) {
            return DomainException.conflict("shipment request conflicts with the existing shipment");
        }
        return DomainException.unavailable("shipping service error: " + exception.getStatusText());
    }
}
