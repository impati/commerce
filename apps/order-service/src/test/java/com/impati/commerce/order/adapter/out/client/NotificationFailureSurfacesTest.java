package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.order.application.port.out.NotificationClient;
import org.junit.jupiter.api.BeforeEach;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 알림 호출 실패가 어댑터에서 사라지지 않는지 확인한다 (ADR-0012).
 *
 * <p>이 작업의 출발점이 정확히 이 지점이었다 — 어댑터가 예외를 통째로 삼켜 응용 계층이 실패를
 * 알 수 없었고, 그래서 재시도할 수도 유실 건수를 셀 수도 없었다.
 *
 * <p>다른 발행 테스트는 {@code NotificationClient}를 대역으로 바꿔 쓰므로 이 성질을 잡지 못한다.
 * 여기서만 실제 HTTP 어댑터가 돈다.
 */
@SpringBootTest(properties = {
        "orders.event-publish-interval=3600000",
        "orders.payment-reconcile-interval=3600000"
})
@RequiresDatabase
@Import(NotificationFailureSurfacesTest.MockServerConfig.class)
class NotificationFailureSurfacesTest {

    private static final String NOTIFICATION_URL = "http://localhost:8109";

    @TestConfiguration
    static class MockServerConfig {
        @Bean
        MockServerRestClientCustomizer mockServerRestClientCustomizer() {
            return new MockServerRestClientCustomizer();
        }
    }

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private MockServerRestClientCustomizer customizer;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = customizer.getServer();
        server.reset();
    }

    @Test
    void serverErrorReachesTheCaller() {
        server.expect(requestTo(NOTIFICATION_URL + "/internal/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> notificationClient.notify(request()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void successReturnsQuietly() {
        server.expect(requestTo(NOTIFICATION_URL + "/internal/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertThatCode(() -> notificationClient.notify(request())).doesNotThrowAnyException();
    }

    private NotificationEventRequest request() {
        return new NotificationEventRequest("OrderPaid", "mem_x", "Order paid", "body", "evt_surface");
    }
}
