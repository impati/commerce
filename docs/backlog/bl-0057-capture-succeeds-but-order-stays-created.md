# 매입은 됐는데 주문이 미결제로 남는 창이 있다

- **ID:** BL-0057
- **기록일:** 2026-08-29

## 배경

[OrderExecutor.checkout](../../apps/order-service/src/main/java/com/impati/commerce/order/application/component/OrderExecutor.java)에서 매입 성공 직후의 커밋이 실패하면 그 사실을 아무도 모른다.

```java
    payment = capture(paymentId);          // 매입 성공
} catch (RuntimeException exception) {
    rollbackBeforeCapture(...);            // 여기는 매입 전 실패 경로다
    throw exception;
}

order.markPaid();
order.attachShipment(shipment.id(), shipment.trackingNumber());
orderChanges.commit(order);                // 여기서 실패하면?
```

`commit`이 실패하면 예외가 `checkout` 밖으로 그대로 나간다. 그 시점 상태는 이렇다.

- 결제는 **CAPTURED** — 사용자 대금이 나갔다
- 주문 행은 **CREATED** — 마지막으로 성공한 커밋이 `attachPayment`다
- `ORDER_PAID` 사건 없음 — 알림도 나가지 않는다
- **`payment_outcome_unknown`이 켜지지 않는다** — 그 표시는 `rollbackBeforeCapture`에서만 켜지는데 이 경로는 거기로 가지 않는다

마지막 항목이 핵심이다. [ADR-0009](../adr/0009-reconcile-unconfirmed-payments.md)의 정리기는 표시된 주문만 후보로 삼으므로 **이 주문을 영영 찾지 못한다.** 사용자는 돈을 냈고 주문은 미결제로 보이며, 그 상태를 발견할 자동 수단이 없다.

[BL-0052](done/bl-0052-order-notification-delivered-once.md)가 만든 것이 아니다. 그 전에도 같은 자리에 `orderRepository.save(order)`가 있었고 창의 모양이 같았다. 다만 그 작업이 "커밋 단위를 지킨다"를 내세웠기 때문에 읽는 사람이 이 구간도 덮인다고 오해하기 쉬워졌다.

**원인은 체크아웃 절차의 진행 상태가 객체가 아니라는 것이다.** 지금 그 상태는 `reservationId`·`paymentId`·`shipment`·`captureAttempted` 네 지역 변수이고, 스택 프레임과 함께 사라진다. "매입까지 갔다"는 사실이 어디에도 남지 않으니 나중에 정리할 근거가 없다.

## 목표

매입이 성공한 뒤 주문 확정에 실패해도 그 사실이 남고, 자동으로 해소되거나 최소한 관측된다.

정해야 할 것은 절차의 진행 상태를 어디에 둘 것인가다. 세 방향이 있다.

- 실패 시 `payment_outcome_unknown`을 켜서 기존 정리기가 찾게 한다. 작지만 그 표시의 뜻("매입 여부를 모른다")과 실제 상황("매입은 확실히 됐고 확정을 못 했다")이 다르다.
- 매입 결과를 별도 상태로 표현해 정리기가 확정을 완료하게 한다.
- 체크아웃 절차 자체를 상태를 가진 것으로 만든다. 프로세스 매니저에 해당하며 재개와 보상이 함께 풀리지만 범위가 가장 크다. 유스케이스가 여럿이 되거나 이 창이 실제로 관측될 때 값이 커진다.

**세 번째를 지금 만들지 않는다.** 체크아웃 유스케이스가 하나뿐인 동안에는 진행 상태가 지역 변수여도 성립하고, 필요해지기 전에 틀을 세우면 그 틀에 끼워 맞추게 된다.
