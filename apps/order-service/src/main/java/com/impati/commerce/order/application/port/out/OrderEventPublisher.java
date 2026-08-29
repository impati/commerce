package com.impati.commerce.order.application.port.out;

import com.impati.commerce.order.domain.OrderModels.OrderEvent;

/**
 * 주문 사건을 바깥으로 내보내는 포트 (ADR-0012).
 *
 * <p>응용 계층은 사건을 넘기고 끝난다. <b>누가 소비하는지, 몇 명인지, HTTP인지 브로커인지
 * 모른다.</b> 알림 서비스를 직접 부르는 것으로 이름 붙이면 어댑터 선택이 응용 계층으로 새어
 * 나오고, 그러면 소비자가 늘거나 전달 수단이 바뀔 때 호출부가 함께 바뀐다.
 *
 * <p><b>발행 성공은 수락이지 소비가 아니다.</b> 지금 구현은 알림 서비스를 동기로 부르므로 두
 * 시점이 같아 보이지만, 브로커가 들어오면 발행이 성공해도 소비는 나중이고 실패할 수도 있다.
 * 이 메서드가 돌아왔다는 것을 "소비자가 처리했다"로 읽는 코드를 쓰지 말 것 — 지금은 통과하고
 * 브로커에서 조용히 깨진다.
 *
 * <p>순서는 {@link OrderEvent#partitionKey()} 단위로만 보장 대상이다. 같은 주문의 사건은
 * 같은 키를 가지므로 브로커에서 한 파티션에 떨어진다. 서로 다른 주문 사이에는 순서가 없다.
 *
 * <p>재시도는 부르는 쪽의 일이다. 실패하면 예외를 던지고, 무엇이 실패했는지 도메인 언어로
 * 옮기는 것까지가 구현의 책임이다.
 */
public interface OrderEventPublisher {
    void publish(OrderEvent event);
}
