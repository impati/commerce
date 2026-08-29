package com.impati.commerce.order.adapter.in.scheduler;

import com.impati.commerce.order.application.port.in.OrderEventPublishUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 사건 아웃박스를 주기적으로 비운다 (ADR-0012).
 *
 * <p>스케줄러가 트리거인 진입점이므로 컨트롤러와 같은 등급이고 {@code adapter/in}에 산다.
 * 애플리케이션을 바깥에서 호출하는 것이지 애플리케이션의 일부가 아니다.
 *
 * <p>인스턴스가 여러 개여도 된다. 같은 사건을 두 번 집는 것은 저장소의 점유가 막고, 장애 중
 * 부하는 최소 간격이 묶는다. 그 둘이 여기가 아니라 저장소에 있는 것이 요점이다 — 진입점을
 * 늘려도 성질이 유지된다.
 */
@Component
public class OrderEventRelay {
    private final OrderEventPublishUseCase orderEventPublishUseCase;

    public OrderEventRelay(OrderEventPublishUseCase orderEventPublishUseCase) {
        this.orderEventPublishUseCase = orderEventPublishUseCase;
    }

    @Scheduled(fixedDelayString = "${orders.event-publish-interval:1000}")
    void publish() {
        orderEventPublishUseCase.publishPending();
    }
}
