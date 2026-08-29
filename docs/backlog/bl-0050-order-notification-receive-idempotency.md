# 주문 알림 수신 경로에 멱등 키가 없다

- **ID:** BL-0050
- **기록일:** 2026-08-29

## 배경

notification-service의 수신 경로 둘 중 주문 알림 쪽([InternalNotificationController](../../apps/notification-service/src/main/java/com/impati/commerce/notification/adapter/in/web/InternalNotificationController.java)의 `/internal/notifications/events`)에는 멱등 키가 없다. `NotificationEventRequest`가 키를 싣지 않고, `record()`가 받은 요청마다 알림 행을 새로 만든다.

[BL-0048](bl-0048-notification-receive-idempotency.md)이 같은 문제를 인증 메일 경로에서 닫았다. 그 작업에서 주문 경로를 범위에서 뺀 이유는 키를 만들 주체가 정해져 있지 않기 때문이다 — member-service는 아웃박스 행 id를 쓸 수 있었지만 order-service에는 아웃박스가 없다. 지금은 `OrderExecutor`의 호출 네 곳이 예외를 삼키며 직접 보낸다.

기록만 남기는 알림은 현재 외부로 나가지 않으므로(`Channel.NONE`) 중복의 대가가 메일 두 통은 아니다. 그러나 알림 목록에 같은 사건이 두 줄로 보이고, 이 경로에 발송이 붙는 순간 대가가 커진다.

[BL-0046](bl-0046-order-notification-loss.md)과 방향이 반대다. 그쪽은 실패한 알림이 사라지는 것이고, 여기는 성공한 알림이 두 번 기록되는 것이다. 다만 BL-0046이 재시도를 붙이는 순간 이 구멍이 실제로 벌어지므로 순서가 얽혀 있다.

## 목표

같은 주문 알림 요청을 여러 번 받아도 한 건만 기록된다.

무엇을 키로 삼을지 정해야 한다. 주문의 상태 전이가 사건 종류별로 한 번만 일어나므로 `주문 id + 이벤트 종류`가 후보이지만, 발신자가 그것을 계약에 실을지 order-service에 아웃박스를 먼저 두고 그 행 id를 쓸지에 따라 [BL-0046](bl-0046-order-notification-loss.md)과의 순서가 달라진다.

`Notification`의 기록 전용 생성자가 키를 `null`로 두고 있고 유니크 제약이 NULL을 서로 다르게 보므로, 키를 붙이는 변경 자체는 마이그레이션 없이 가능하다.
