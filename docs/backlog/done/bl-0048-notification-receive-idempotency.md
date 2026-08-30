# 같은 알림 요청을 두 번 받으면 두 번 보낸다

- **ID:** BL-0048
- **기록일:** 2026-08-25

## 배경

notification-service의 수신 경로 둘 — `/internal/notifications/events`와 `/internal/notifications/email-verifications` — 은 받은 요청마다 알림 행을 새로 만든다. 같은 요청이 두 번 오면 행이 둘 생기고 메일이 두 통 나간다.

**부르는 쪽이 재시도할 수 있어야 한다는 것과, 받는 쪽이 중복을 보내도 된다는 것은 다르다.** 호출이 타임아웃 나면 발신자는 처리됐는지 알 수 없고, 그 상태에서 할 수 있는 선택은 재시도뿐이다 — 포기하면 유실이다. 그래서 발신 경로는 at-least-once가 맞다. 중복을 없애는 일은 결과를 아는 쪽, 즉 수신측이 해야 한다.

지금은 그 분업이 없다. 발신측이 재시도를 붙이는 만큼 중복이 그대로 사용자에게 간다. [BL-0004](bl-0004-member-service-outbox.md)가 member-service에 재시도를 붙이면서 이 구멍이 실제로 열렸고, [BL-0052](bl-0052-order-notification-delivered-once.md)이 order-service에 붙이면 두 배가 된다.

같은 증상을 내는 [BL-0047](bl-0047-notification-dispatch-has-no-claim.md)과는 원인이 다르다. 그쪽은 아웃박스 한 행을 여러 인스턴스가 집는 것이고, 여기는 행이 애초에 둘 생기는 것이다. 둘 다 막아야 "중복 발송하지 않는다"가 성립한다.

## 목표

같은 발송 요청을 여러 번 받아도 알림이 한 번만 기록되고 한 번만 발송된다.

무엇을 같은 요청으로 볼지 정해야 한다. 발신자가 부여한 키를 계약([ApiContracts](../../../libs/common-contracts/src/main/java/com/impati/commerce/common/ApiContracts.java))에 실을지, 내용으로 판정할지에 따라 두 수신 경로와 두 발신 서비스가 함께 움직인다. 중복 판정의 유효 기간도 정해야 한다 — 무기한이면 정상적인 재발송까지 막힌다.
