# 주문 취소가 멱등하지 않다

- **ID:** BL-0062
- **기록일:** 2026-08-30

## 배경

[Order.cancel](../../apps/order-service/core/src/main/java/com/impati/commerce/order/domain/OrderModels.java)이 `DELIVERED`만 막는다.

```java
public void cancel(String reason) {
    if (status.equals("DELIVERED")) {
        throw DomainException.conflict("delivered order cannot be cancelled");
    }
    this.status = "CANCELLED";
    record(OrderEventType.ORDER_CANCELLED, ...);
}
```

이미 `CANCELLED`인 주문에 다시 부르면 통과하고 **`ORDER_CANCELLED` 사건이 하나 더 쌓인다.** 상태는 그대로지만 사건은 늘어난다.

전이가 사건을 만드는 구조에서 이건 "같은 전이가 두 번 일어났다"고 말하는 것이다. 상태를 바꾸지 않았으니 전이가 아니었는데도 그렇다 — [ADR-0012](../adr/0012-order-events-as-outbox.md)가 세운 *"사건은 상태가 아니라 전이에서 나온다"* 와 어긋난다.

세 가지가 따라온다.

**사용자가 취소 알림을 두 번 받는다.** 사건 id가 다르므로 소비측 멱등 키가 막지 못한다. 재시도로 인한 중복이 아니라 실제로 두 번 일어난 사건이기 때문이다.

**임차 계산의 전제가 깨진다.** [OrderEventPublishExecutor](../../apps/order-service/boot/worker/src/main/java/com/impati/commerce/order/application/component/OrderEventPublishExecutor.java)가 한 주문의 사건 수를 `OrderEventType.values().length`로 잡고 그것으로 임차가 배치를 덮는지 검증한다. 근거는 "전이가 한 방향이니 종류마다 한 번"인데 `cancel`이 그걸 지키지 않는다. 사건이 상한을 넘으면 임차가 배치보다 짧아지고, 그러면 같은 주문을 두 인스턴스가 갖게 되어 [ADR-0016](../adr/0016-publish-order-events-to-kafka.md)의 순서 보장이 무너진다.

**두 번째 취소가 조용하다.** 보상이나 정리가 이미 취소된 주문을 다시 취소해도 아무 신호가 없어, 그런 호출이 실제로 일어나고 있는지 알 수 없다.

**이중 취소가 실재하는 경로는 확인하지 못했다.** 결제 정리는 배타 점유로 돌고 체크아웃 보상은 한 번만 돈다. 지금 재현되지 않더라도 **전제가 코드로 지켜지지 않는다는 사실**은 그대로다.

## 목표

같은 전이를 두 번 요청해도 사건이 하나다. 상태를 바꾸지 않는 호출은 사건을 남기지 않는다.

정해야 할 것은 **두 번째 호출의 응답**이다. 조용히 성공시키는 것(멱등)과 거절하는 것(`conflict`) 중 어느 쪽인지에 따라 체크아웃 보상과 결제 정리의 처리가 달라진다. 취소 사유가 이미 있는 것과 다를 때 무엇을 남길지도 함께 정해야 한다 — 첫 사유가 실제 원인이므로 덮어쓰지 않는 쪽이 자연스럽지만, 그러면 두 번째 사유는 어디에도 남지 않는다.

`cancel` 하나만 볼지 다른 전이도 함께 볼지도 범위 결정이다. `markDelivered`는 `FULFILLING`과 `PAID`에서만 받으므로 두 번 부르면 거절되고, `markPaid`와 `attachShipment`도 앞 상태를 검사한다. **취소만 예외다.**
