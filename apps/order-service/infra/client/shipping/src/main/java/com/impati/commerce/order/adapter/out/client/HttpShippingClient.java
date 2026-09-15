package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.DownstreamError;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.order.application.port.out.ShippingClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

@Component
public class HttpShippingClient implements ShippingClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpShippingClient(RestClient shippingRestClient, ServiceCallExecutor calls) {
        this.restClient = shippingRestClient;
        this.calls = calls;
    }

    @Override
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        return calls.command("shipment creation", () -> restClient.post()
                    .uri("/internal/shipments")
                    .body(request)
                    .retrieve()
                    .body(ShipmentResponse.class), HttpShippingClient::translateConflict);
    }

    @Override
    public ShipmentResponse cancelShipment(String shipmentId) {
        return calls.command("shipment cancellation", () -> restClient.post()
                    .uri("/internal/shipments/{shipmentId}/cancel", shipmentId)
                    .retrieve()
                    .body(ShipmentResponse.class), HttpShippingClient::translateConflict);
    }

    @Override
    public Optional<ShipmentResponse> shipmentForOrder(String orderId) {
        return calls.optionalQuery("order shipment lookup", () -> restClient.get()
                .uri("/internal/shipments/orders/{orderId}", orderId)
                .retrieve()
                .body(ShipmentResponse.class));
    }

    private static void translateConflict(DownstreamError error) {
        if (error.hasCode("conflict")) {
            throw DomainException.conflict("shipment request conflicts with the existing shipment");
        }
    }
}
