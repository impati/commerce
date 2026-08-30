# ADR-0003: 노출하지 않을 경로를 `/internal` 프리픽스로 선언한다

- **상태:** 승인됨
- **날짜:** 2026-08-16
- **해결한 백로그:** BL-0017 게이트웨이가 노출하는 경로와 내부 경로를 구분한다
- **관련:** [ADR-0002](0002-network-segmentation-as-trust-boundary.md)

## 맥락과 의도

ADR-0002는 서비스 간 신뢰 경계를 배포 토폴로지로 강제하기로 했다. 그 결정을 실행하려면 각 서비스에서 **무엇을 노출하면 안 되는지**가 표현되어야 하는데, 지금은 코드를 읽어서 알 수 없다. 등급이 드러나는 것은 member-service의 `/members/internal/**` 하나뿐이고, 나머지 6개 서비스는 두 등급이 같은 컨트롤러에 섞여 있다.

등급의 기준부터 정해야 한다. "형제 서비스만 부르는가"는 기준이 되지 못한다 — 게이트웨이 자신이 `/members/internal/sessions/resolve`를 부르고, `/carts`와 `/products`는 게이트웨이와 형제 서비스가 함께 부른다. 기준은 **게이트웨이가 브라우저에 노출하는가**다.

## 결정

**게이트웨이가 노출하지 않는 경로를 각 서비스의 `/internal` 최상위 프리픽스 아래로 모은다.** 컨트롤러도 `Internal*Controller`로 분리해 등급이 클래스 단위로 드러나게 한다.

선언은 경로가 하고, **강제는 애플리케이션이 하지 않는다.** 앱은 여전히 호출자를 확인하지 않는다 (ADR-0002와 일관). 프리픽스는 보안 통제가 아니라 배포 계층이 소비할 수 있는 **선언**이다.

프리픽스를 최상위에 두는 이유는 하나의 패턴으로 표현되기 위해서다. `/members/internal`처럼 리소스 아래에 두면 경로 중간에 와일드카드가 필요해 인그레스·프록시가 다루기 나빠진다. 이미 그 형태였던 member-service도 `/internal/members/**`로 옮긴다.

## 고려한 선택지

### 어노테이션 (`@InternalApi`) — 채택하지 않음

선언 지점에서 의도가 드러나고 테스트로 일관성을 강제할 수 있다. 그러나 **인그레스·리버스 프록시·메시 authz는 어노테이션을 읽지 못한다.** 강제하는 주체가 애플리케이션 밖에 있는 이상 선언은 경로나 포트여야 한다.

어노테이션과 경로를 함께 두고 "붙은 것은 프리픽스를 가져야 한다"고 검증하는 방안도 봤으나, 검증되는 것은 둘 사이의 일치뿐이다. 어느 경로가 내부인지는 여전히 사람이 판단하고, 어노테이션을 빠뜨리면 테스트도 조용하다. 순환이므로 값어치가 없다.

### 문서 표 — 채택하지 않음

싸지만 코드와 어긋난다. 두 벌이 되면 반드시 갈라진다.

### 게이트웨이 라우팅을 정본으로 선언 — 채택하지 않음

`GatewayController`가 이미 노출 목록이므로 추가 작업이 거의 없다. 그러나 하위 서비스 쪽에서는 등급이 여전히 보이지 않고, 인그레스 규칙을 쓰려면 다른 모듈의 코드를 읽어야 한다.

### 프로세스·모듈 분리 — 지금 하지 않음

다른 포트에 띄우면 규약이 아니라 물리적 분리라 사람이 실수할 여지가 없다. 가장 강한 강제이며 종착점이다. 다만 지금 하면 6개 서비스에 실행 단위를 늘리게 되고, 그 범위는 [BL-0054](../backlog/done/bl-0054-split-api-and-worker-modules.md)다. **이 결정은 그 분리의 선행 단계다** — 나중에 쪼갤 때 프리픽스가 절단면이 된다.

## 범위

- **포함:** 게이트웨이가 노출하지 않는 11개 경로를 `/internal` 아래로 옮기고, 호출하는 클라이언트와 테스트를 맞춘다. 게이트웨이가 내부 경로를 외부에 뚫지 않았는지 보는 테스트를 추가한다
- **제외:** 앱 레벨의 호출자 검증(ADR-0002에서 하지 않기로 함), 인그레스 설정(저장소 밖), 프로세스 분리(BL-0012)

## 등급 판정

게이트웨이가 노출하지 않으므로 `/internal` 아래로 가는 경로.

| 서비스 | 이전 | 이후 | 근거 |
| --- | --- | --- | --- |
| member | `/members/internal/sessions/resolve` | `/internal/members/sessions/resolve` | 세션 토큰을 신원으로 바꾼다 |
| member | `/members/internal/{memberId}` | `/internal/members/{memberId}` | 임의 회원 조회 |
| cart | `POST /carts/clear` | `POST /internal/carts/clear` | order의 checkout 후처리 |
| catalog | `GET /skus/{skuId}` | `GET /internal/skus/{skuId}` | order·cart의 가격 조회 |
| inventory | `POST /reservations`, `.../commit`, `.../release` | `/internal/reservations...` | order의 saga |
| inventory | `POST /stock` | `POST /internal/stock` | 재입고·운영용. 호출자 없음 |
| payment | `POST /payments/capture` | `POST /internal/payments/capture` | order의 결제 |
| shipping | `POST /shipments` | `POST /internal/shipments` | order의 배송 생성 |
| shipping | `GET /shipments/{shipmentId}` | `GET /internal/shipments/{shipmentId}` | 호출자 없음. 조회 등급이 내부다 |
| notification | `POST /notifications/email-verifications` | `POST /internal/notifications/email-verifications` | member의 인증 메일 요청 |
| notification | `GET /notifications/outbox` | `GET /internal/notifications/outbox` | 운영용. 호출자 없음 |

게이트웨이가 노출하므로 그대로 두는 경로: `/display/home`, `/products`, `/products/{id}`, `/members`(가입·로그인·로그아웃·인증·재발송), `/members/me`, `/members/me/addresses`, `/carts`, `/carts/items`, `/checkouts`, `/orders/{id}`, `/orders/{id}/delivered`, `/shipments/{id}/ship`, `/shipments/{id}/deliver`, `GET /stock`, `GET /notifications`.

## 완료 기준

- 위 11개 경로가 `/internal` 아래에 있고 호출자가 모두 갱신됐다
- 게이트웨이의 외부 매핑에 `/internal`이 없다는 테스트가 있다
- `make verify` 통과

## 결과와 제약

**분류가 맞는지는 자동으로 검증되지 않는다.** `apps/*`가 서로 의존하지 않으므로 어떤 테스트도 "이 경로를 형제 서비스만 부른다"를 알 수 없다. 새 경로의 등급 판단은 사람에게 남고, 이 문서가 그 판단의 기준을 제공한다.

자동으로 잡히는 것은 하나다 — 내부 경로를 게이트웨이가 외부에 그대로 뚫는 사고.

**프리픽스는 아무것도 막지 않는다.** 인그레스가 그 패턴을 막아야 효력이 생기고, 그 설정은 이 저장소 밖에 있다.

## 재검토 조건

| 조건 | 그때의 방향 |
| --- | --- |
| 내부 경로가 실수로 노출되는 일이 실제로 발생한다 | 프로세스·포트 분리(BL-0012)로 올린다 |
| 한 서비스의 내부 경로가 서비스별로 다른 권한을 요구하게 된다 | 프리픽스로는 표현되지 않는다. 메시 authz로 간다 |
