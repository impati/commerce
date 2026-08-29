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
     * 실패를 그대로 올린다.
     *
     * <p>예전에는 여기서 예외를 삼켰다. 결제까지 끝난 주문을 알림 실패로 되돌릴 수 없다는 이유는
     * 타당했지만, "되돌리지 않는다"와 "없던 일로 한다"는 다르다 — 삼키면 응용 계층이 실패를 알 수
     * 없어 재시도할 수도, 세어볼 수도 없다. 지금은 부르는 쪽이 아웃박스라 실패가 상태로 남고
     * 재시도되므로 삼킬 이유가 없다 (ADR-0012).
     *
     * <p>프로토콜 오류를 도메인 언어로 옮기지는 않는다. 클라이언트 전체에 일관되게 적용할 일이라
     * 별도 항목(BL-0036, BL-0037)으로 둔다.
     */
    @Override
    public void notify(NotificationEventRequest request) {
        restClient.post().uri("/internal/notifications/events").body(request).retrieve().toBodilessEntity();
    }
}
