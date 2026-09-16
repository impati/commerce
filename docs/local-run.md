# 로컬 실행 가이드

Storefront의 상품 탐색, 장바구니와 구매 흐름을 로컬에서 확인한다. 서비스별 포트는 [README](../README.md)의 표를 본다.

## 준비물

- JDK 21, Node 20+, 실행 중인 Docker와 Docker Compose.
- jq: 수동 API 확인과 데모 스크립트에 사용한다.
- Gradle은 wrapper를 사용한다.

## 실행

저장소 루트에서:

```bash
make boot-all
```

MySQL(호스트 3316)과 Kafka(호스트 9192)를 자동으로 준비하고 bootJar를 빌드한다. BFF를 포함한 14개 실행 단위를 로컬 프로세스로 띄우며, 모두 health 검사를 통과하면 `all services are running`을 출력한다.

별도 터미널에서:

```bash
make frontend-install
make frontend-dev
```

http://localhost:5173 에 접속한다. Gateway는 8080, Storefront BFF는 8110이다. 브라우저 요청은 Gateway를 거친다.

코드를 바꿨는데 이미 서비스가 실행 중이면 `make stop` 후 다시 `make boot-all`을 실행한다. 살아 있는 프로세스는 자동 교체하지 않는다. `SKIP_BUILD=true ./scripts/run-all.sh`는 현재 jar로 중지된 실행 단위만 올릴 때 사용한다.

## 데모 회원과 시드

기본 `local` 프로파일에서 자동으로 생성한다.

| 데이터 | 초기 값 |
| --- | --- |
| 회원 | `demo@impati.test` / `demo-password`, 인증 완료, 기본 배송지 1개 |
| 상품 | 티셔츠, 드립 세트, 파우치 3개 상품과 SKU 5개 |
| 가격 | 티셔츠 29,000원, 드립 세트 87,000원·91,000원, 파우치 34,000원 |
| 재고 | SKU마다 20개, 총 100개 |
| 장바구니·주문 | 사전 생성하지 않는다. 직접 담고 주문한다 |
| 결제 대역 | `card_test_success` 성공, `card_test_decline` 거절 |

회원은 데모 이메일이 없을 때, 상품과 재고는 각 저장소가 비었을 때만 생성한다. 재시작해서 재고를 다시 채우거나 상품을 중복 생성하지 않는다.

## 장바구니와 주문 확인

로그인 후 상품을 담으면 Cart 저장 결과를 먼저 받고 BFF의 화면 데이터를 별도로 조회한다. 화면의 상품 합계는 Order가 계산한 견적이다. 상품 정보 실패는 해당 상품에 표시하고, 견적·재고 확인 실패는 결제를 제한한다. 장바구니 조회나 담기 실패를 데모 성공으로 바꾸지 않는다.

결제할 때 확인한 견적을 전달한다. 다른 탭에서 상품·수량을 바꾸거나 가격이 달라지면 새 장바구니와 견적을 확인해야 한다. 접수한 구매분은 장바구니에서 분리되며 결제 실패 시 자동 복원하지 않는다. 응답을 잃은 주문은 같은 요청 키와 같은 본문으로 결과를 회수한다.

명령으로 수동 확인하려면:

```bash
make demo
```

로그인 → 지면 → 담기 → 견적 조회 → 확인된 주문 접수 → 출고 → 배송 완료 → 알림 조회를 호출한다. 이 스크립트는 응답을 눈으로 보는 데모이며 검증은 `make verify`다.

상품 탐색 데이터는 API 실패 시 데모 데이터로 표시될 수 있다. 화면의 연결 상태 배지와 실제 요청 결과를 함께 확인한다. 상품이 보인다는 이유만으로 백엔드가 연결됐다고 판단하지 않는다. 구매 화면은 데모 데이터로 대체하지 않는다.

## 데이터 보존과 초기화

저장소가 있는 8개 서비스는 하나의 MySQL에서 서비스별 데이터베이스를 사용한다. Gateway, BFF와 Display는 자체 저장소가 없다. Flyway가 각 서비스의 마이그레이션을 기동 시 적용한다.

`make stop`은 로컬 서비스 프로세스만 종료한다. MySQL과 Kafka 컨테이너는 유지되며, 회원·재고·주문 등도 남는다.

전체 데이터를 삭제하고 처음부터 시작할 때만:

```bash
make stop
docker compose down -v
make boot-all
```

기존 로컬 데이터가 삭제된다.

## 로그와 문제 확인

- 로그: `.run/<실행 단위>.log`, BFF는 `.run/storefront-bff.log`.
- PID: `.run/<실행 단위>.pid`.
- 시작 실패 시 로그와 `docker compose ps`를 확인한다.
- 3316이나 9192를 다른 프로젝트가 쓰면 자동 기동은 멈춘다. 접속 대상은 명시적으로 지정해야 한다.
- BFF health가 정상이어도 의존 서버 조회가 실패할 수 있다. 장바구니 화면의 영역별 오류와 로그를 확인한다.
- `make verify`는 Docker가 필요하며 실제 MySQL·Kafka 통합 테스트를 포함한다.
- 프론트 검증: `make frontend-test`, `make frontend-build`.

## 모든 백엔드를 컨테이너로 실행

```bash
./gradlew bootJar
docker compose up --build
```

이미지 안에서 빌드하지 않으므로 bootJar를 먼저 만든다. 서비스 URL은 compose의 환경변수로 전달한다. 시드가 필요하면 서비스에 `SPRING_PROFILES_ACTIVE=local`을 지정한다. 호스트 프로세스와 컨테이너의 서비스 포트를 동시에 사용하지 않는다.
