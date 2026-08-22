# 체크아웃 보상이 실패하면 원인이 사라지고 주문이 중간 상태로 남는다

- **ID:** BL-0033
- **기록일:** 2026-08-22

## 배경

[OrderService.checkout](../../apps/order-service/src/main/java/com/impati/commerce/order/application/OrderService.java)의 catch 블록은 예약 해제 → 주문 취소 → 저장 → 알림 → 원래 예외 재던지기 순으로 돈다. 이 보상 자체가 실패할 수 있다는 것을 다루지 않는다.

`inventory.releaseReservation`은 HTTP 호출이므로 상대 장애나 타임아웃으로 던질 수 있고, `orders.save`도 DB 오류로 던질 수 있다. 둘 중 하나가 던지면 마지막 `throw exception`에 도달하지 못한다. 결과가 둘이다.

- **원래 실패 원인이 사라진다.** 보상 과정의 예외가 대신 올라가므로, 체크아웃이 왜 실패했는지가 로그에도 응답에도 남지 않는다.
- **주문이 중간 상태로 남는다.** 예약 해제가 던지면 `order.cancel()`이 실행되지 않아 주문이 `CREATED`로 영구히 남고, 재고 예약도 풀리지 않은 채 남는다.

알림은 이 경로에 해당하지 않는다. [HttpNotificationClient](../../apps/order-service/src/main/java/com/impati/commerce/order/adapter/out/client/HttpNotificationClient.java)가 예외를 삼키기 때문이다. 그 구간의 문제는 [BL-0004](bl-0004-member-service-outbox.md)가 담고 있다.

[PD-0004](../policy/pd-0004-checkout-and-compensation.md)의 R6과 R7은 보상이 성공한다는 전제로 쓰여 있다. 보상이 실패했을 때 무엇이 참이어야 하는지를 정하지 않는다.

## 목표

보상이 실패해도 체크아웃의 원래 실패 원인이 유실되지 않는다. 보상을 끝내지 못한 주문은 중간 상태로 방치되지 않고, 그런 주문을 찾아낼 방법이 있다.
