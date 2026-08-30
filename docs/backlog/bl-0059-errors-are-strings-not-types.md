# 실패를 문자열 메시지로 다룬다

- **ID:** BL-0059
- **기록일:** 2026-08-29

## 배경

무엇이 실패했는지를 코드가 예외 메시지 문자열로 들고 다닌다.

- [OrderEventPublishExecutor](../../apps/order-service/boot/worker/src/main/java/com/impati/commerce/order/application/component/OrderEventPublishExecutor.java)가 `failure.getMessage()`를 사건의 `lastError`에 넣는다.
- [OrderExecutor](../../apps/order-service/boot/api/src/main/java/com/impati/commerce/order/application/component/OrderExecutor.java)의 보상 경로가 `cause.getMessage()`를 취소 사유로 넘기고, 그것이 사용자에게 가는 알림 본문이 된다.

문자열이라서 세 가지가 따라온다.

**길이가 통제되지 않는다.** 하위 서비스가 돌려주는 응답 본문이 그대로 예외 메시지에 실린다. [BL-0052](done/bl-0052-order-notification-delivered-once.md) 리뷰에서 이것이 저장 컬럼을 넘겨 취소 자체를 롤백시키거나 재시도 한도를 무력화할 수 있다는 것이 드러나 도메인에서 자르는 것으로 막았다. **막은 것은 증상이다** — 자를 필요가 있다는 사실 자체가 다루는 단위가 틀렸다는 신호다.

**분류할 수 없다.** "일시적 장애라 재시도할 값어치가 있나", "설정 오류라 재시도가 무의미한가"를 판정하려면 문자열을 봐야 한다. 지금 재시도는 그 구분 없이 한도까지 돈다.

**사용자에게 그대로 나간다.** 취소 사유가 알림 본문에 들어가므로 하위 서비스의 내부 오류 문구가 사용자에게 보인다.

[BL-0036·BL-0037](bl-0036-clients-leak-protocol-errors.md)과 맞닿아 있다. 그쪽은 프로토콜 오류를 도메인 언어로 옮기는 일이고, 이 항목은 옮긴 뒤 그것을 무엇으로 들고 다닐지다. 그쪽이 먼저 서면 여기서 쓸 타입이 생긴다.

## 목표

실패가 타입으로 표현되고, 저장·재시도 판정·사용자 표현이 각각 그 타입에서 나온다. 자르기가 필요 없어지거나, 필요하더라도 자유 문자열이 아니라 부가 정보에만 적용된다.

정해야 할 것은 타입의 범위다. 사건의 `lastError`가 운영 진단용인지 재시도 판정에 쓰이는지에 따라 담을 내용이 달라지고, 사용자에게 보일 문구를 실패 타입에서 만들지 아니면 소비자가 정할지도 함께 정해야 한다.
