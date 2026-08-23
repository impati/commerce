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
| `order-service` | 8108 | 주문 aggregate, checkout saga |
| `notification-service` | 8109 | 알림 이벤트 기록 |

## 실행

```bash
make build
make boot-all
make demo
make stop
```

`make boot-all`은 전체 bootJar를 만든 뒤 각 서비스를 로컬 프로세스로 실행합니다.
API Gateway는 `http://localhost:8080` 입니다.

프론트엔드:

```bash
make frontend-install
make frontend-dev
```

Storefront는 기본적으로 `http://localhost:5173`에서 열립니다.

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

`POST /login`은 접근 토큰과 세션 토큰을 함께 돌려준다. **요청에 붙이는 것은 접근 토큰**이고, 5분 뒤 만료되면 세션 토큰으로 `POST /sessions/refresh`를 불러 새로 받는다. 게이트웨이는 접근 토큰을 공개키로 직접 검증하므로 요청마다 member-service를 부르지 않는다 ([ADR-0007](docs/adr/0007-hybrid-session-tokens.md)).

접근 토큰이 필요한 경로 (`Authorization: Bearer <accessToken>`):

```http
GET  /me
POST /me/addresses
GET  /cart
POST /cart/items
POST /checkout
GET  /orders/{orderId}
GET  /notifications
POST /shipments/{shipmentId}/ship
POST /shipments/{shipmentId}/deliver
```

세션 토큰이 필요한 경로 (`Authorization: Bearer <sessionToken>`):

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
  -d '{"email":"demo@impati.test","password":"demo-password"}' | jq -r .token)

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
