package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.NotificationEventRequest;
import com.impati.commerce.order.application.port.out.NotificationClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpNotificationClient implements NotificationClient {
    private final RestClient restClient;

    public HttpNotificationClient(RestClient notificationRestClient) {
        this.restClient = notificationRestClient;
    }

    /**
     * 알림 실패를 삼킨다. 결제까지 끝난 주문을 알림 때문에 되돌리지 않기 위한 것이지만,
     * 실패하면 아무도 알 수 없어 알림 유실이 조용히 쌓인다. 아웃박스로 대체할 자리다.
     */
    @Override
    public void notify(NotificationEventRequest request) {
        try {
            restClient.post().uri("/internal/notifications/events").body(request).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
            // TODO 아웃박스 도입 시 제거. 지금은 실패가 기록되지 않는다. BL-0004.
        }
    }
}
