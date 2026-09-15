package com.impati.commerce.order.adapter.out.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.http.ServiceCallExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpPaymentClientTest {
    private static final String BASE_URL = "http://payment.test";

    private MockRestServiceServer server;
    private HttpPaymentClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpPaymentClient(builder.build(), new ServiceCallExecutor(new ObjectMapper()));
    }

    @Test
    void translatesOnlyRecognizedPaymentConflict() {
        server.expect(times(1), requestTo(BASE_URL + "/internal/payments/authorize"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"payment_declined\",\"message\":\"issuer detail\"}"));
        server.expect(times(1), requestTo(BASE_URL + "/internal/payments/authorize"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"validation_error\",\"message\":\"internal mismatch\"}"));
        var request = new AuthorizePaymentRequest("ord_1", "mem_1", Money.krw(1_000), "card");

        assertThatThrownBy(() -> client.authorizePayment(request))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("payment_declined"));
        assertThatThrownBy(() -> client.authorizePayment(request))
                .isInstanceOfSatisfying(DomainException.class,
                        failure -> assertThat(failure.code()).isEqualTo("downstream_error"));
        server.verify();
    }
}
