package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.NotificationEventRequest;

/**
 * notification-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다.
 *
 * <p>실패하면 예외를 던진다. 삼키면 부르는 쪽이 재시도할 수도, 실패 건수를 셀 수도 없다.
 * 재시도와 종단 판단은 아웃박스를 비우는 쪽이 한다 (ADR-0012).
 */
public interface NotificationClient {
    void notify(NotificationEventRequest request);
}
