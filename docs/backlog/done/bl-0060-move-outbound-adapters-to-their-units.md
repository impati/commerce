# 나가는 어댑터가 쓰지 않는 실행 단위까지 따라간다

- **ID:** BL-0060
- **기록일:** 2026-08-30

## 배경

[BL-0054](bl-0054-split-api-and-worker-modules.md)가 실행 단위를 API와 워커로 나눴지만 절단면이 `adapter/in`뿐이었다. **나가는 어댑터는 전부 `core`에 남아 둘 다에게 간다.**

order-service를 보면 실제 사용이 갈린다.

| 실행 단위 | 쓰는 클라이언트 |
| --- | --- |
| api | member, cart, catalog, inventory, payment, shipping (6) |
| worker | payment, notification (2) |
| **겹치는 것** | **payment 하나** |

그런데 [CommerceRestClients](../../../apps/order-service/infra/client/core/src/main/java/com/impati/commerce/order/adapter/out/client/CommerceRestClients.java)가 `@Configuration`이라 **두 컨텍스트 모두에서 일곱 개 빈을 만들고 `@Value("${clients.X.url}")`로 일곱 개 URL을 요구한다.** 워커는 부르지도 않는 다섯 서비스의 설정이 없으면 뜨지 않는다.

**[ADR-0002](../../adr/0002-network-segmentation-as-trust-boundary.md)가 신뢰 경계를 네트워크 분리로 강제하기로 했는데 코드가 그 경계를 드러내지 않는다.** 워커를 member·cart에 닿지 않는 망에 두어도 아무것도 그것을 말해주지 않는다.

**그리고 카프카가 이것을 드러낸다.** [BL-0055](bl-0055-publish-order-events-to-kafka.md)에서 워커의 발행 어댑터가 카프카로 바뀌어도 HTTP 클라이언트 다섯은 그대로 따라온다. "워커는 카프카만 알면 된다"가 성립하지 않는다. **그래서 BL-0055보다 앞이어야 한다** — 뒤에 하면 브로커 의존이 자리를 잡은 뒤에 옮기게 된다.

## 목표

각 실행 단위가 자기가 실제로 쓰는 협력자만 안다. 어떤 서비스에 닿아야 하는지가 모듈 의존으로 드러나고, 닿지 않는 서비스의 설정을 요구하지 않는다.

정해야 할 것은 겹치는 어댑터의 자리다. `HttpPaymentClient`는 API와 워커가 둘 다 쓰는데, 중복해서 둘지 공유 모듈을 하나 더 둘지에 따라 모듈 구성이 달라진다. 겹치는 것이 하나뿐이라면 공유 모듈을 만드는 값이 크지 않을 수도 있다.

`core`에 무엇이 남는지도 함께 정한다. 도메인·응용·포트는 확실하고, 저장소 어댑터는 둘 다 쓰므로 남을 후보다.
