package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.CreateShipmentRequest;
import com.impati.commerce.common.ApiContracts.ShipmentResponse;
import com.impati.commerce.order.application.ShippingClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpShippingClient implements ShippingClient {
    private final RestClient restClient;

    public HttpShippingClient(RestClient shippingRestClient) {
        this.restClient = shippingRestClient;
    }

    @Override
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        return restClient.post().uri("/shipments").body(request).retrieve().body(ShipmentResponse.class);
    }
}
