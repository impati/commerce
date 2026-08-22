package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.order.application.port.out.InventoryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpInventoryClient implements InventoryClient {
    private final RestClient restClient;

    public HttpInventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    @Override
    public ReservationResponse reserve(ReserveInventoryRequest request) {
        return restClient.post().uri("/internal/reservations").body(request).retrieve().body(ReservationResponse.class);
    }

    @Override
    public void commitReservation(String reservationId) {
        restClient.post().uri("/internal/reservations/{reservationId}/commit", reservationId).retrieve().toBodilessEntity();
    }

    @Override
    public void releaseReservation(String reservationId) {
        restClient.post().uri("/internal/reservations/{reservationId}/release", reservationId).retrieve().toBodilessEntity();
    }
}
