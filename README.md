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

세션 토큰이 필요한 경로 (`Authorization: Bearer <token>`):

```http
GET  /me
POST /me/addresses
GET  /cart
POST /cart/items
POST /checkout
GET  /orders/{orderId}
GET  /notifications
POST /logout
POST /shipments/{shipmentId}/ship
POST /shipments/{shipmentId}/deliver
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

## 문서

- [로컬 실행 가이드](docs/local-run.md)
- [시스템 아키텍처](docs/architecture.md)
- [도메인 경계](docs/domain-map.md)
- 기술 결정 기록(ADR): [docs/adr/](docs/adr/)
- 백로그(앞으로 할 후보): [docs/backlog/](docs/backlog/)
- 겪은 문제와 기술 조사: [problem/](problem/)
- 변경 이력: [CHANGELOG.md](CHANGELOG.md)

## 프론트엔드 구조

```text
frontend/storefront/
  src/App.tsx        # 상품 탐색, 장바구니, checkout, 배송 진행 화면
  src/api.ts         # API Gateway client와 demo fallback
  src/mockData.ts    # 백엔드 미기동 시 사용하는 seed 데이터
  src/styles.css     # storefront UI
```
