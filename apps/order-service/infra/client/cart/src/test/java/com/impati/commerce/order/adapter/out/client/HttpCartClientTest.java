package com.impati.commerce.order.adapter.out.client;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpCartClientTest {
    private static final String BASE_URL = "http://cart.test";

    private MockRestServiceServer server;
    private HttpCartClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpCartClient(builder.build(), new ServiceCallExecutor(new ObjectMapper()));
    }

    @Test
    void translatesStructuredCartEmptyError() {
        server.expect(requestTo(BASE_URL + "/internal/carts/checkout"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"cart_empty\",\"message\":\"downstream detail\"}"));

        assertThatThrownBy(() -> client.checkout("mem_1", "ord_1", 1))
                .isInstanceOfSatisfying(DomainException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("cart_empty");
                    assertThat(failure.getMessage()).isEqualTo("cart is empty");
                });
        server.verify();
    }
}
