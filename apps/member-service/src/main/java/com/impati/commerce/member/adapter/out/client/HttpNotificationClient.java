package com.impati.commerce.member.adapter.out.client;

import com.impati.commerce.common.ApiContracts.EmailVerificationMailRequest;
import com.impati.commerce.member.application.port.out.NotificationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpNotificationClient implements NotificationClient {
    private final RestClient restClient;

    public HttpNotificationClient(
            RestClient.Builder builder,
            @Value("${clients.notification.url}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
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
