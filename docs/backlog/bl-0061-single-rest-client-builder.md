# 빌더 주입을 없애고 공용 팩토리가 클라이언트를 만든다

- **ID:** BL-0061
- **기록일:** 2026-08-30

## 배경

`RestClient.Builder`는 주입 지점마다 새 인스턴스가 온다. 그래서 어댑터가 각자 주입받으면 빌더가 어댑터 수만큼 생기고, 빌더 단위로 동작하는 것들이 갈라진다 — [common-http](../../libs/common-http)의 타임아웃 커스터마이저와 테스트의 `MockServerRestClientCustomizer`가 그렇다.

이 규칙은 지금 텍스트로만 서 있다. CLAUDE.md 한 줄과 그것을 지키는 클래스 둘이다. 실제 상태를 세어보면 이렇다.

| 위치 | 형태 | 클라이언트 수 |
| --- | --- | --- |
| [CommerceRestClients](../../apps/order-service/infra/client/core/src/main/java/com/impati/commerce/order/adapter/out/client/CommerceRestClients.java) | 수집자 | 6 |
| [GatewayRestClients](../../apps/api-gateway/src/main/java/com/impati/commerce/gateway/adapter/out/client/GatewayRestClients.java) | 수집자 | 여러 개 |
| [display HttpCatalogClient](../../apps/display-service/src/main/java/com/impati/commerce/display/adapter/out/client/HttpCatalogClient.java) | 직접 주입 | 1 |
| [cart HttpCatalogClient](../../apps/cart-service/src/main/java/com/impati/commerce/cart/adapter/out/client/HttpCatalogClient.java) | 직접 주입 | 1 |
| [member HttpNotificationClient](../../apps/member-service/src/main/java/com/impati/commerce/member/adapter/out/client/HttpNotificationClient.java) | 직접 주입 | 1 |

세 서비스가 이미 어기고 있다. 클라이언트가 하나씩이라 아직 드러나지 않을 뿐이고 **두 번째가 붙는 순간 깨진다.** 지키는 두 곳은 같은 클래스를 두 번 쓴 것이다.

[BL-0060](bl-0060-move-outbound-adapters-to-their-units.md)에서 실제로 깨졌다. 클라이언트를 협력자별 모듈로 나누면서 각 모듈이 빌더를 직접 주입받게 했고, order-service의 클라이언트가 일곱으로 늘어 빌더가 일곱 개 생겼다. 다른 테스트가 우연히 걸려서 발견됐다. 모듈이 늘수록 어길 기회가 는다.

## 목표

빌더를 아무도 주입받지 않는다. `libs/common-http`가 팩토리 하나를 자동 구성으로 제공하고, 그것이 프로세스에 하나뿐인 빌더를 갖는다. 어댑터는 기준 URL만 준다.

그러면 `CommerceRestClients`와 `GatewayRestClients`가 사라지고, 세 서비스의 위반이 함께 없어지며, 커스터마이저가 규율이 아니라 구조로 하나의 빌더에 묶인다.

정해야 할 것은 **불가능하게 만들 것인가**다. 부트 모듈이 스프링의 `RestClientAutoConfiguration`을 제외하면 빌더 빈 자체가 없어져 주입하려는 순간 기동이 실패한다. 대신 제외 선언이 실행 단위마다 한 줄씩 생기고, 그것을 빠뜨리면 다시 규율로 돌아간다. 제외 없이 팩토리만 두면 관례로만 선다.
