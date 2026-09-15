package com.impati.commerce.member.adapter.out.client;

import com.impati.commerce.common.ApiContracts.EmailVerificationMailRequest;
import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.http.RestClientFactory;
import com.impati.commerce.http.ServiceCallExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpNotificationClient implements NotificationClient {
    private final RestClient restClient;
    private final ServiceCallExecutor calls;

    public HttpNotificationClient(
            RestClientFactory restClients,
            ServiceCallExecutor calls,
            @Value("${clients.notification.url}") String baseUrl
    ) {
        this.restClient = restClients.forBaseUrl(baseUrl);
        this.calls = calls;
    }

    @Override
    public void requestEmailVerification(String memberId, String email, String token, String idempotencyKey) {
        calls.command("verification notification request", () -> restClient.post()
                .uri("/internal/notifications/email-verifications")
                .body(new EmailVerificationMailRequest(memberId, email, token, idempotencyKey))
                .retrieve()
                .toBodilessEntity());
    }
}
