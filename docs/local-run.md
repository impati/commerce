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

- **`Gateway connected`** (초록) — 5개 리소스 전부 실제 데이터
- **`Partial — demo: ...`** (파랑) — 나열된 것만 `src/mockData.ts` 시드로 대체, 나머지는 실제 데이터
- **`Demo mode`** (노랑) — 5개 전부 실패. 화면 전체가 시드 데이터

Demo mode에서도 상품이 보이므로 **화면만 보고 백엔드가 붙었다고 판단하면 안 된다.** 배지를 먼저 본다.

백엔드만 따로 확인하려면:

```bash
curl -s http://localhost:8080/display/home | jq '.sections[] | {key, count: (.products | length)}'
```

시드 데이터에서는 `daily_essentials` 2건, `new_arrivals` 2건, `premium_picks` 1건이 나온다. 섹션이 비어 있으면 catalog의 상품 태그(`daily`/`new`/`premium`)와 display의 섹션 정의가 어긋난 것이다 — 지면 필터는 태그 기준이다.

## 상품 노출만 보려면 몇 개를 띄워야 하나

**3개면 된다.**

| 서비스 | 왜 필요한가 |
| --- | --- |
| api-gateway | 프론트의 유일한 진입점 |
| display-service | `/display/home` 섹션 구성 |
| catalog-service | display가 상품/SKU를 물어본다 |

프론트 초기 로딩은 `home`, `products`, `cart`, `inventory`, `notifications`를 각각 독립적으로 가져오므로, 살아있는 것은 실제 데이터를 쓰고 죽은 것만 시드로 대체된다 ([App.tsx](../frontend/storefront/src/App.tsx)의 `fetchStorefront`). cart / inventory / notification 3개를 죽이고 확인하면 상품은 실제 가격으로 뜨고 배지가 `Partial — demo: cart, inventory, notifications`가 된다.

**5개 전부를 실제 데이터로 보려면** 여기에 cart-service, inventory-service, notification-service를 더해 6개다. member / payment / shipping / order 4개는 지면 노출에 필요 없고 checkout을 눌렀을 때 필요하다.

재고 숫자로 실제/시드를 구분할 수 있다. 시드 합계는 100이고, 체크아웃을 한 번이라도 했으면 실제 값은 그보다 작다.

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

## 데이터가 남는 범위

저장소가 있는 8개 서비스가 모두 H2 파일 DB를 쓴다. 프로세스를 전부 재시작해도 상품, 재고, 회원, 장바구니, 주문, 결제, 배송, 알림이 그대로 남는다. api-gateway와 display-service는 저장소가 없다.

```bash
ls .data/            # 서비스마다 .mv.db 파일 하나
rm -rf .data         # 초기화 (make stop 후에)
```

`.data/`는 상대경로이므로 저장소 루트에서 실행해야 그 자리에 생긴다. 스키마는 각 서비스의 `src/main/resources/db/migration`에 있고 Flyway가 기동 시 적용한다.

시드 데이터는 저장소가 비어 있을 때만 들어간다. 재시작해도 상품이 6개로 늘거나 재고가 두 배가 되지 않는다.

데이터를 비우고 처음부터 보려면 `make stop` 후 `rm -rf .data`를 실행하고 다시 띄운다.

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
| `Demo mode`가 뜬다 | 게이트웨이가 아예 안 떴다. 5개 호출이 전부 실패한 상태다 |
| `Partial — demo: ...`가 뜬다 | 나열된 리소스의 서비스만 죽었다. `.run/<service>.log`를 본다 |
| `did not become healthy` | 포트 충돌이 대부분이다. `lsof -i :8080` 등으로 확인 |
| 상품이 비어 있다 | catalog-service가 죽었거나 태그가 어긋났다 |
| 재시작했는데 예전 데이터가 남아 있다 | 정상이다. `.data/`의 파일 DB에 남는다. 초기화는 `make stop` 후 `rm -rf .data` |
| 재고가 이상하다 | 체크아웃한 만큼 차감된 실제 값이다. 시드 합계는 100이다 |
| `jq: command not found` | `make demo`가 jq를 쓴다. `brew install jq` |

## docker compose 대안

[docker-compose.yml](../docker-compose.yml)로도 띄울 수 있다. 서비스 간 URL은 컨테이너 이름으로 환경변수(`CLIENTS_*_URL`)를 통해 주입된다.

```bash
./gradlew bootJar
docker compose up --build
```

[docker/service.Dockerfile](../docker/service.Dockerfile)이 이미 빌드된 jar를 복사하는 구조이므로 **`bootJar`를 먼저 돌려야 한다.** 이미지 안에서 빌드하지 않는다.

로컬 개발에는 `make boot-all`이 더 빠르다. compose는 서비스 간 네트워킹을 컨테이너 이름으로 확인하고 싶을 때 쓴다.
