package com.impati.commerce.member.adapter.out.client;

import com.impati.commerce.common.ApiContracts.EmailVerificationMailRequest;
import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.http.RestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpNotificationClient implements NotificationClient {
    private final RestClient restClient;

    public HttpNotificationClient(
            RestClientFactory restClients,
            @Value("${clients.notification.url}") String baseUrl
    ) {
        this.restClient = restClients.forBaseUrl(baseUrl);
    }

    @Override
    public void requestEmailVerification(String memberId, String email, String token, String idempotencyKey) {
        restClient.post()
                .uri("/internal/notifications/email-verifications")
                .body(new EmailVerificationMailRequest(memberId, email, token, idempotencyKey))
                .retrieve()
                .toBodilessEntity();
    }
}
