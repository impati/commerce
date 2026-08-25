# 주문 알림 실패가 어댑터에서 사라진다

- **ID:** BL-0046
- **기록일:** 2026-08-25

## 배경

[HttpNotificationClient](../../apps/order-service/src/main/java/com/impati/commerce/order/adapter/out/client/HttpNotificationClient.java)가 알림 호출의 예외를 통째로 삼킨다.

```java
try {
    restClient.post().uri("/internal/notifications/events").body(request).retrieve().toBodilessEntity();
} catch (RuntimeException ignored) {
}
```

삼키는 자리가 **어댑터**인 것이 문제다. 응용 계층은 실패했다는 사실 자체를 알 수 없으므로 재시도할 수도, 기록할 수도, 판단할 수도 없다. 프로토콜 오류를 도메인 언어로 옮기는 것이 어댑터의 일인데 여기서는 오류를 옮기는 대신 없앤다.

삼키기로 한 이유 자체는 타당하다. `OrderExecutor`의 알림 호출 네 곳 중 셋은 매입이 끝난 뒤이고([PD-0012-R8](../policy/pd-0012-checkout-and-compensation.md)), 결제까지 끝난 주문을 알림 실패로 되돌릴 수는 없다. 그러나 "되돌리지 않는다"와 "없던 일로 한다"는 다르다. 지금은 `OrderPaid` · `ShipmentCreated` · `OrderCancelled` · `OrderDelivered` 네 종류가 조용히 사라지고, 사라진 건수를 세는 수단이 없다.

BL-0004와 원인이 다르다. 그쪽은 호출이 DB 트랜잭션 안에 있는 것이 문제였고, 여기서는 `checkout`이 saga라 트랜잭션이 애초에 없다. 공통은 유실뿐이다.

## 목표

알림 발송 실패가 응용 계층에 도달하고, 유실되지 않고 재시도되며, 한도를 넘긴 것이 상태로 남아 관측된다. 그러면서도 알림 실패가 이미 성립한 주문을 되돌리지 않는다.

발송 의도를 어디에 커밋할지 정해야 한다. `checkout`은 `@Transactional`이 아니고 `orderRepository.save`가 건별로 커밋되므로, BL-0004처럼 "상태 변경과 같은 트랜잭션에 기록"이 그대로 성립하지 않는다.

수신측이 중복을 걸러야 하는 문제는 [BL-0048](bl-0048-notification-receive-idempotency.md)에 있다.
