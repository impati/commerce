# 배송 완료 경로의 등급을 주석이 선언한다

- **ID:** BL-0058
- **기록일:** 2026-08-29

## 배경

[OrderController](../../apps/order-service/src/main/java/com/impati/commerce/order/adapter/in/web/OrderController.java)의 배송 완료 처리가 공개 경로에 있으면서 주석으로만 내부라고 말한다.

```java
/** shipping 흐름에서 게이트웨이가 부르는 내부 경로. 배송 완료 처리는 회원 요청이 아니다. */
@PostMapping("/orders/{orderId}/delivered")
OrderResponse delivered(@PathVariable String orderId) { ... }
```

확인한 사실은 셋이다.

- 게이트웨이 컨트롤러에 이 경로의 매핑이 **없다.** 브라우저에 노출되지 않는다.
- 실제 호출은 `POST /shipments/{shipmentId}/deliver` 안에서 일어난다.
- order-service에는 `Internal*Controller`가 **하나도 없다.**

[CLAUDE.md](../../CLAUDE.md)의 기준과 어긋난다 — 게이트웨이가 브라우저에 노출하지 않는 경로는 `/internal` 아래에 두고 `Internal*Controller`가 담는다. 기준은 "누가 부르는가"가 아니라 "게이트웨이가 노출하는가"이며, 여기서는 노출하지 않는다.

**[BL-0017](done/bl-0017-classify-internal-endpoints.md)이 없애려던 상태 그 자체다.** [ADR-0003](../adr/0003-internal-path-prefix.md)이 프리픽스를 도입한 이유가 "각 서비스에서 무엇을 노출하면 안 되는지가 코드로 드러나지 않아 그 결정을 실행할 수 없었다"였는데, 여기서는 그 선언을 주석이 대신하고 있다. 주석은 등급이 아니다.

**지금 뚫려 있다는 뜻은 아니다.** 실제 차단은 배포 토폴로지가 하고([ADR-0002](../adr/0002-network-segmentation-as-trust-boundary.md)) 프리픽스는 등급을 선언할 뿐이다. 문제는 경로 단위로 노출을 나눌 때 이 엔드포인트가 절단면 밖에 놓인다는 것이다.

덧붙여 이 엔드포인트에는 소유권 검사가 없다. 같은 컨트롤러의 조회는 `X-Member-Id`를 받아 주문 주인을 확인하는데 이쪽은 받지 않는다. 내부 등급이면 그것이 맞지만, 공개 등급인 채로 소유권 검사가 없는 조합은 등급이 잘못 붙었을 때 대가가 크다 — 임의의 주문을 배송 완료로 만들면 주인에게 알림이 가고, 완료된 주문은 취소할 수 없게 된다.

## 목표

배송 완료 경로의 등급이 주석이 아니라 경로와 클래스로 드러난다. order-service도 다른 서비스와 같은 모양이 된다.

게이트웨이의 클라이언트가 함께 움직여야 한다. 옮기면서 소유권 검사를 어떻게 할지도 정한다 — 내부 등급으로 확정하면 지금처럼 받지 않는 것이 맞고, 그 판단을 명시적으로 남긴다.
