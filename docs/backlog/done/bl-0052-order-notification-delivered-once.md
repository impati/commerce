# 주문 알림이 유실되거나 두 번 기록된다

- **ID:** BL-0052
- **기록일:** 2026-08-29

## 배경

order-service의 알림 발신과 notification-service의 주문 알림 수신이 양쪽 다 뚫려 있다. 두 구멍은 방향이 반대이고 원인이 하나다.

**나가는 쪽은 실패가 사라진다.** `HttpNotificationClient`(BL-0055에서 사라졌다)가 알림 호출의 예외를 통째로 삼킨다.

```java
try {
    restClient.post().uri("/internal/notifications/events").body(request).retrieve().toBodilessEntity();
} catch (RuntimeException ignored) {
}
```

삼키는 자리가 **어댑터**인 것이 문제다. 응용 계층은 실패했다는 사실 자체를 알 수 없으므로 재시도할 수도, 기록할 수도, 판단할 수도 없다. 프로토콜 오류를 도메인 언어로 옮기는 것이 어댑터의 일인데 여기서는 오류를 옮기는 대신 없앤다.

삼키기로 한 이유 자체는 타당하다. `OrderExecutor`의 알림 호출 네 곳 중 셋은 매입이 끝난 뒤이고, 결제까지 끝난 주문을 알림 실패로 되돌릴 수는 없다. 그러나 "되돌리지 않는다"와 "없던 일로 한다"는 다르다. 지금은 `OrderPaid` · `ShipmentCreated` · `OrderCancelled` · `OrderDelivered` 네 종류가 조용히 사라지고, 사라진 건수를 세는 수단이 없다.

**들어오는 쪽은 같은 요청이 두 건이 된다.** [InternalNotificationController](../../../apps/notification-service/boot/api/src/main/java/com/impati/commerce/notification/adapter/in/web/InternalNotificationController.java)의 `/internal/notifications/events`에는 멱등 키가 없다. `NotificationEventRequest`가 키를 싣지 않고, 받은 요청마다 알림 행을 새로 만든다. 기록만 남기는 알림은 현재 외부로 나가지 않으므로(`Channel.NONE`) 중복의 대가가 메일 두 통은 아니지만, 알림 목록에 같은 사건이 두 줄로 보이고 이 경로에 발송이 붙는 순간 대가가 커진다.

**두 구멍을 따로 막을 수 없다.** 나가는 쪽에 재시도를 붙이는 순간 들어오는 쪽의 중복이 실제로 벌어진다 — [BL-0048](bl-0048-notification-receive-idempotency.md)이 "BL-0046이 order-service에 붙이면 두 배가 된다"고 예고한 것이 이것이다. 반대로 수신 멱등만 먼저 붙이면 무엇을 키로 삼을지 정할 근거가 없다. 두 항목이 각각 남겨둔 미결정이 같은 질문이기 때문이다 — order-service에 아웃박스를 두는가. 그 답이 발송 의도를 커밋할 자리와 멱등 키의 출처를 동시에 결정한다.

member-service는 같은 문제를 아웃박스([BL-0004](bl-0004-member-service-outbox.md)) → 수신 멱등([BL-0048](bl-0048-notification-receive-idempotency.md)) 순으로 이미 닫았고, 키 의미와 배타 점유 방식은 [ADR-0011](../../adr/0011-deliver-verification-mail-once.md)에 있다.

이 항목은 BL-0046(발신측 유실)과 BL-0050(수신측 멱등)을 대체한다.

## 목표

주문 알림이 유실되지 않고 중복 기록되지도 않는다.

- 알림 발송 실패가 응용 계층에 도달하고, 유실되지 않고 재시도되며, 한도를 넘긴 것이 상태로 남아 관측된다.
- 알림 실패가 이미 성립한 주문을 되돌리지 않는다.
- 같은 주문 알림 요청을 여러 번 받아도 한 건만 기록된다.

정해야 할 것은 발송 의도를 어디에 커밋하느냐다. `checkout`은 `@Transactional`이 아니고 `orderRepository.save`가 건별로 커밋되므로, BL-0004처럼 "상태 변경과 같은 트랜잭션에 기록"이 그대로 성립하지 않는다. 그 결정이 멱등 키의 출처도 함께 정한다 — 아웃박스 행 id를 쓸지, `주문 id + 이벤트 종류`를 계약에 실을지.

`Notification`의 기록 전용 생성자가 키를 `null`로 두고 있고 유니크 제약이 NULL을 서로 다르게 보므로, 수신측에 키를 붙이는 변경 자체는 마이그레이션 없이 가능하다.
