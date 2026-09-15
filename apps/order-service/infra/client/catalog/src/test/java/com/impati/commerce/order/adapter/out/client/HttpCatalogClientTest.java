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

class HttpCatalogClientTest {
    private static final String BASE_URL = "http://catalog.test";

    private MockRestServiceServer server;
    private HttpCatalogClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpCatalogClient(builder.build(), new ServiceCallExecutor(new ObjectMapper()));
    }

    @Test
    void translatesMissingCheckoutSkuToCartChanged() {
        server.expect(requestTo(BASE_URL + "/internal/skus/sku_missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"not_found\",\"message\":\"sku missing\"}"));

        assertThatThrownBy(() -> client.sku("sku_missing"))
                .isInstanceOfSatisfying(DomainException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("cart_changed");
                    assertThat(failure.status()).isEqualTo(409);
                });
        server.verify();
    }
}
