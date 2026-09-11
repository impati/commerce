package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.order.application.port.out.InventoryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import com.impati.commerce.common.DomainException;
import java.util.Optional;

@Component
public class HttpInventoryClient implements InventoryClient {
    private final RestClient restClient;

    public HttpInventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    @Override
    public ReservationResponse reserve(ReserveInventoryRequest request) {
        try {
            return restClient.post().uri("/internal/reservations").body(request).retrieve().body(ReservationResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getResponseBodyAsString().contains("out_of_stock")) {
                throw DomainException.outOfStock("insufficient stock");
            }
            throw DomainException.unavailable("inventory service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.outcomeUnknown("inventory reservation outcome unknown: " + exception.getMessage());
        }
    }

    @Override
    public void commitReservation(String reservationId) {
        mutate(reservationId, "commit");
    }

    @Override
    public void releaseReservation(String reservationId) {
        mutate(reservationId, "release");
    }

    @Override
    public Optional<ReservationResponse> reservationForOrder(String orderId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/reservations/orders/{orderId}", orderId)
                    .retrieve().body(ReservationResponse.class));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw DomainException.unavailable("inventory service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.unavailable("inventory lookup failed: " + exception.getMessage());
        }
    }

    private void mutate(String reservationId, String action) {
        try {
            restClient.post().uri("/internal/reservations/{reservationId}/{action}", reservationId, action)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw DomainException.unavailable("inventory service error: " + exception.getStatusText());
        } catch (ResourceAccessException exception) {
            throw DomainException.outcomeUnknown("inventory " + action + " outcome unknown: " + exception.getMessage());
        }
    }
}
