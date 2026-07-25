# 로컬 실행 가이드

상품이 지면에 노출되는 것부터 checkout, 배송 완료까지 로컬에서 보는 방법. 서비스별 포트는 [README](../README.md)의 표를 본다.

## 준비물

| 도구 | 용도 | 확인 |
| --- | --- | --- |
| JDK 21 | 서비스 실행 | `java -version` |
| Node 20+ | storefront | `node --version` |
| jq | 데모 스크립트 | `jq --version` |

Gradle은 wrapper를 쓰므로 따로 설치하지 않는다.

## 가장 빠른 길

```bash
make boot-all
```

10개 서비스를 bootJar로 빌드해 로컬 프로세스로 띄우고, 전부 `/actuator/health`가 200을 반환할 때까지 기다린다. 마지막에 `all services are running`이 찍히면 성공이다.

프론트엔드는 별도 터미널에서:

```bash
make frontend-install
make frontend-dev
```

`http://localhost:5173`을 열면 지면이 보인다.

## 지면이 제대로 붙었는지 확인하는 법

화면 우측 상단 배지가 판정 기준이다.

- **`Gateway connected`** — 백엔드에서 받아온 실제 데이터
- **`Demo mode`** — 게이트웨이 호출이 실패해서 `src/mockData.ts`의 시드로 그린 화면

Demo mode에서도 상품이 보이므로 **화면만 보고 백엔드가 붙었다고 판단하면 안 된다.** 배지를 먼저 본다.

백엔드만 따로 확인하려면:

```bash
curl -s http://localhost:8080/display/home | jq '.sections[] | {key, count: (.products | length)}'
```

시드 데이터에서는 `daily_essentials` 2건, `new_arrivals` 2건, `premium_picks` 1건이 나온다. 섹션이 비어 있으면 catalog의 상품 태그(`daily`/`new`/`premium`)와 display의 섹션 정의가 어긋난 것이다 — 지면 필터는 태그 기준이다.

## 상품 노출만 보려면 몇 개를 띄워야 하나

10개 전부는 필요 없지만, **6개는 필요하다.**

| 서비스 | 왜 필요한가 |
| --- | --- |
| api-gateway | 프론트의 유일한 진입점 |
| display-service | `/display/home` 섹션 구성 |
| catalog-service | display와 cart가 상품/SKU를 물어본다 |
| cart-service | 첫 화면에서 장바구니를 조회한다 |
| inventory-service | 첫 화면에서 재고를 조회한다 |
| notification-service | 첫 화면에서 알림 목록을 조회한다 |

프론트의 초기 로딩이 `home`, `products`, `cart`, `inventory`, `notifications`를 `Promise.all`로 한꺼번에 부르고, **하나만 실패해도 전체가 Demo mode로 떨어지기** 때문이다 ([api.ts](../frontend/storefront/src/api.ts), [App.tsx](../frontend/storefront/src/App.tsx)). 지면만 보려는데 Demo mode가 뜬다면 재고나 알림 서비스가 죽어 있는 경우가 많다.

member / payment / shipping / order 4개는 지면 노출에는 필요 없고 checkout을 눌렀을 때 필요하다.

## 전체 흐름 확인

```bash
make demo
```

지면 조회 → 장바구니 담기 → checkout → 출고 → 배송 완료 → 알림 확인을 순서대로 호출한다. 결제 성공 토큰(`card_test_success`)을 쓰므로 주문은 `FULFILLING`까지 간다.

**이 스크립트에는 assert가 없다.** 응답을 눈으로 보는 수동 확인용이며 검증 수단이 아니다. 검증은 `./gradlew test`다.

결제 실패 경로를 보려면 토큰을 바꿔서 직접 호출한다:

```bash
curl -sS -X POST http://localhost:8080/checkout -H 'Content-Type: application/json' \
  -d '{"memberId":"mem_demo","paymentToken":"card_test_decline"}' | jq
```

주문은 `CANCELLED`가 되고 재고 예약은 release되며 장바구니는 유지된다.

## 로그와 종료

```bash
make stop
```

- 로그: `.run/<service>.log`
- PID: `.run/<service>.pid`
- 특정 서비스만 다시 띄우려면 `SKIP_BUILD=true ./scripts/run-all.sh`. 이미 살아 있는 것은 건너뛰고 죽은 것만 올린다.

## 자주 겪는 문제

| 증상 | 원인과 대처 |
| --- | --- |
| `Demo mode`가 뜬다 | 게이트웨이가 안 떴거나, 위 6개 중 하나가 죽었다. `.run/*.log`를 본다 |
| `did not become healthy` | 포트 충돌이 대부분이다. `lsof -i :8080` 등으로 확인 |
| 상품이 비어 있다 | catalog-service가 죽었거나 태그가 어긋났다 |
| 새로고침하니 주문이 사라졌다 | 모든 저장소가 인메모리다. 프로세스를 재시작하면 데이터가 날아간다 |
| `jq: command not found` | `make demo`가 jq를 쓴다. `brew install jq` |

## docker compose 대안

[docker-compose.yml](../docker-compose.yml)로도 띄울 수 있다. 서비스 간 URL은 컨테이너 이름으로 환경변수(`CLIENTS_*_URL`)를 통해 주입된다.

```bash
./gradlew bootJar
docker compose up --build
```

[docker/service.Dockerfile](../docker/service.Dockerfile)이 이미 빌드된 jar를 복사하는 구조이므로 **`bootJar`를 먼저 돌려야 한다.** 이미지 안에서 빌드하지 않는다.

로컬 개발에는 `make boot-all`이 더 빠르다. compose는 서비스 간 네트워킹을 컨테이너 이름으로 확인하고 싶을 때 쓴다.
