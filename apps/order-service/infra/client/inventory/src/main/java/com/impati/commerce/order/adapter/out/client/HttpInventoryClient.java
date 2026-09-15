package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.common.ApiContracts.ReserveInventoryRequest;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.ServiceCallExecutor;
import com.impati.commerce.order.application.port.out.InventoryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

@Component
public class HttpInventoryClient implements InventoryClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpInventoryClient(RestClient inventoryRestClient, ServiceCallExecutor calls) {
        this.restClient = inventoryRestClient;
        this.calls = calls;
    }

    @Override
    public ReservationResponse reserve(ReserveInventoryRequest request) {
        return calls.command("inventory reservation", () -> restClient.post()
                .uri("/internal/reservations")
                .body(request)
                .retrieve()
                .body(ReservationResponse.class), error -> {
            if (error.hasCode("out_of_stock")) {
                throw DomainException.outOfStock("insufficient stock");
            }
        });
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
        return calls.optionalQuery("inventory reservation lookup", () -> restClient.get()
                .uri("/internal/reservations/orders/{orderId}", orderId)
                .retrieve()
                .body(ReservationResponse.class));
    }

    private void mutate(String reservationId, String action) {
        calls.command("inventory reservation " + action, () -> restClient.post()
                .uri("/internal/reservations/{reservationId}/{action}", reservationId, action)
                .retrieve()
                .toBodilessEntity());
    }
}
