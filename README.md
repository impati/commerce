# Impati Commerce

Java 21 + Spring Boot 3.2 기반 이커머스 마이크로서비스 레퍼런스입니다.

목표는 “상품 전시 -> 장바구니 -> 주문 -> 결제 -> 배송 완료” 흐름을 실제 서비스 경계로 쪼개서 볼 수 있게 만드는 것입니다. 각 서비스는 독립 Spring Boot 애플리케이션이며, 로컬에서는 인메모리 저장소와 HTTP 동기 호출로 동작합니다.

프론트엔드는 `frontend/storefront`에 React + Vite + TypeScript로 구성했습니다. API Gateway가 켜져 있으면 실제 백엔드 흐름을 호출하고, 꺼져 있으면 데모 데이터로 화면을 유지합니다.

## 서비스 구성

| 서비스 | 포트 | 책임 |
| --- | ---: | --- |
| `api-gateway` | 8080 | 외부 진입점, 서비스 조합 |
| `member-service` | 8101 | 회원, 배송지 |
| `catalog-service` | 8102 | 상품, SKU, 가격 스냅샷 |
| `display-service` | 8103 | 홈 전시 섹션 |
| `inventory-service` | 8104 | 재고, 예약, 확정, 해제 |
| `cart-service` | 8105 | 장바구니 |
| `payment-service` | 8106 | 결제 승인/매입 시뮬레이션 |
| `shipping-service` | 8107 | 배송 생성, 출고, 배송 완료 |
| `order-service` / api | 8108 | 주문 aggregate, checkout saga |
| `order-service` / worker | 8118 | 사건 발행, 결제 미확인 정리 |
| `notification-service` / api | 8109 | 알림 수신·조회 |
| `notification-service` / worker | 8119 | 메일 발송 |
| `notification-service` / consumer | 8129 | 주문 사건 구독 |

**두 서비스는 실행 단위가 나뉘어 있습니다** ([ADR-0014](docs/adr/0014-split-api-and-worker-modules.md)). 요청을 받는 것과 큐를 비우는 것은 스케일 축이 다르고, 브로커 의존성이 API에 붙으면 안 되기 때문입니다. 절단면은 `adapter/in`의 종류입니다 — 컨트롤러는 api, 스케줄러는 worker, 브로커 구독은 consumer. notification은 셋이고 order는 둘이라 실행 단위가 13개입니다 ([ADR-0016](docs/adr/0016-publish-order-events-to-kafka.md)).

## 실행

여덟 서비스가 MySQL 하나를 공유하고 서비스마다 데이터베이스를 나눕니다. **`make boot-all`이 DB까지 챙깁니다** — 떠 있지 않으면 띄우고 준비될 때까지 기다립니다.

```bash
make boot-all
make demo
make stop
```

`make boot-all`은 bootJar를 만든 뒤 각 서비스를 로컬 프로세스로 실행합니다. 초기화는 `docker compose down -v`입니다.

호스트 포트는 **3316**입니다. 3306은 흔한 포트라 다른 프로젝트의 MySQL과 부딪힙니다. 그 포트를 이미 다른 것이 쓰고 있으면 `make boot-all`은 **남의 DB에 마이그레이션을 돌리지 않으려고 멈춥니다.**
API Gateway는 `http://localhost:8080` 입니다.

## 검증

```bash
make verify
```

**도커가 필요합니다.** 저장소를 가진 여덟 서비스의 테스트가 Testcontainers로 띄운 실제 MySQL 위에서 돕니다. 대역으로 검증하면 점유의 잠금 동작처럼 DB마다 다른 것이 검증되지 않은 채 남습니다 ([ADR-0013](docs/adr/0013-real-database-in-the-harness.md)).

컨테이너를 매번 새로 띄우지 않으려면 `~/.testcontainers.properties`에 다음을 넣습니다.

```
testcontainers.reuse.enable=true
```

도커 없이 도는 것만 보려면 `make test-fast`입니다. pre-commit 훅이 이 형태로 돌며, **커밋이 통과했다고 전체가 통과한 것은 아닙니다.**

프론트엔드:

```bash
make frontend-install
make frontend-dev
```

Storefront는 기본적으로 `http://localhost:5173`에서 열립니다.

`/orders`는 회원 주문 내역, `/orders/{orderId}`는 주문 상세입니다. 직접 진입하거나 새로고침해도 세션을 확인하고, 비로그인 상태에서는 로그인 후 같은 주소의 주문을 이어서 조회합니다. 주문 데이터 조회 실패는 데모 주문으로 대체하지 않습니다. 배포 시에도 프론트 서버가 이 경로들을 `index.html`로 연결하도록 SPA fallback을 설정해야 합니다.

주문 화면의 컴포넌트 테스트는 `make frontend-test`, 타입 검사와 프로덕션 빌드는 `make frontend-build`로 확인합니다.

## 주요 API

인증이 필요 없는 경로:

```http
GET  /display/home
GET  /products
GET  /inventory
POST /members                    회원 가입
POST /members/verifications      이메일 소유 확인
POST /login
```

`POST /login`은 접근 토큰을 본문으로 돌려주고, 14일 세션 토큰은 `HttpOnly` 쿠키로 설정한다. **일반 요청에 붙이는 것은 접근 토큰**이고, 5분 뒤 만료되면 브라우저가 세션 쿠키와 함께 `POST /sessions/refresh`를 불러 새로 받는다. 접근 토큰은 프론트 메모리에만 두므로 페이지를 새로 열 때도 같은 방식으로 다시 발급한다. 게이트웨이는 접근 토큰을 공개키로 직접 검증하므로 요청마다 member-service를 부르지 않는다 ([ADR-0007](docs/adr/0007-hybrid-session-tokens.md), [ADR-0020](docs/adr/0020-browser-session-cookie.md)).

접근 토큰이 필요한 경로 (`Authorization: Bearer <accessToken>`):

```http
GET  /me
POST /me/addresses
GET  /cart
POST /cart/items
POST /checkout
GET  /orders?cursor={cursor}&size=20
GET  /orders/{orderId}
GET  /orders/{orderId}/checkout-result
GET  /notifications
POST /shipments/{shipmentId}/ship
POST /shipments/{shipmentId}/deliver
```

주문 목록은 `{ items, nextCursor }`를 응답합니다. 기본 크기는 20건이며 1~100건을 허용합니다. `nextCursor`는 마지막 주문의 UTC 생성 시각과 ID를 함께 담은 불투명한 값이며 다음 요청에 그대로 전달합니다. `null`이면 마지막 페이지입니다. 생성 시각 내림차순, 동일 시각에서는 ID 내림차순으로 조회합니다.

접수된 주문은 처리 중에도 목록에 나타납니다. 고객용 `checkoutResult`는 `PROCESSING`(주문 처리 중), `CHECKING`(운영 확인 중), `SUCCEEDED`, `FAILED`이며, 성공한 주문의 `orderStatus`는 현재 주문 생애주기를 나타냅니다. 보상 중인 주문은 최종 실패로 표시하지 않습니다. 상세에는 주문 시점 상품·배송지 스냅샷, 기록된 운송장과 사건 발생 시각이 있는 타임라인을 포함하고 내부 결제·재고 식별자와 발행·오류 정보는 제외합니다. 다른 회원의 주문은 없는 주문과 같은 404 응답입니다 ([PD-0020](docs/policy/pd-0020-order-history-visibility.md), [ADR-0022](docs/adr/0022-member-order-history.md)).

세션 쿠키가 필요한 경로:

```http
POST /sessions/refresh           접근 토큰 재발급
POST /logout                     세션 폐기
```

**퍼블릭 경로는 `memberId`를 받지 않는다.** 게이트웨이가 세션을 검증해 하위 서비스에
`X-Member-Id`로 신원을 넘긴다.

데모 데이터 (`local` 프로파일에서만 생성됨):

- 회원: `demo@impati.test` / 비밀번호 `demo-password`
- SKU: `sku_tee_white_m`, `sku_tee_black_l`, `sku_drip_ivory`, `sku_drip_moss`, `sku_pouch_sage`
- 결제 성공 토큰: `card_test_success`
- 결제 실패 토큰: `card_test_decline`

## Checkout 예시

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@impati.test","password":"demo-password"}' | jq -r .accessToken)

curl -X POST http://localhost:8080/cart/items \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"skuId":"sku_tee_white_m","quantity":2}'

curl -X POST http://localhost:8080/checkout \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"paymentToken":"card_test_success"}'
```

결제 실패 토큰을 쓰면 주문은 `CANCELLED`가 되고, 재고 예약은 release됩니다. 장바구니는 유지되어 재시도할 수 있습니다.

결제는 승인과 매입으로 나뉘어 있고 매입이 배송 생성 뒤에 옵니다. 매입 전에 실패하면 배송·승인·예약이 모두 되돌아가므로 사용자에게 흔적이 남지 않습니다. 같은 주문에 결제는 하나만 존재하므로 승인을 여러 번 요청해도 이중 청구가 되지 않습니다.

매입 요청의 응답을 받지 못한 경우는 실패와 다르게 다룹니다. 매입이 멱등하므로 한 번 더 시도해 확정하고, 두 번 모두 확인하지 못하면 주문을 되돌리되 결제 미확인으로 표시해 나중에 환불로 정리할 수 있게 남깁니다.

## 문서

- [로컬 실행 가이드](docs/local-run.md)
- [시스템 아키텍처](docs/architecture.md)
- [도메인 경계](docs/domain-map.md)
- 기술 결정 기록(ADR): [docs/adr/](docs/adr/)
- 백로그(앞으로 할 후보): [docs/backlog/](docs/backlog/)
- 겪은 문제와 기술 조사: [problem/](problem/)

## 프론트엔드 구조

```text
frontend/storefront/
  src/App.tsx        # 상품 탐색, 장바구니, checkout, 배송 진행 화면
  src/api.ts         # API Gateway client와 demo fallback
  src/mockData.ts    # 백엔드 미기동 시 사용하는 seed 데이터
  src/styles.css     # storefront UI
```
